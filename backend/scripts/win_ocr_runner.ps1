param([string]$ImagePath)

$ErrorActionPreference = "Stop"

try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime -ErrorAction Stop

    [Windows.Security.Cryptography.CryptographicBuffer, Windows.Security.Cryptography, ContentType = WindowsRuntime] | Out-Null
    [Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
    [Windows.Storage.Streams.IRandomAccessStream, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null
    [Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType = WindowsRuntime] | Out-Null
    [Windows.Graphics.Imaging.SoftwareBitmap, Windows.Graphics.Imaging, ContentType = WindowsRuntime] | Out-Null
    [Windows.Media.Ocr.OcrEngine, Windows.Media.Ocr, ContentType = WindowsRuntime] | Out-Null
    [Windows.Globalization.Language, Windows.Globalization, ContentType = WindowsRuntime] | Out-Null

    function AwaitTask($asyncOp, [Type]$returnType) {
        $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
            $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1
        })[0]
        $asTask = $asTaskGeneric.MakeGenericMethod($returnType)
        $task = $asTask.Invoke($null, @($asyncOp))
        $task.Wait()
        return $task.Result
    }

    $fullPath = (Resolve-Path $ImagePath).Path
    $fileOp = [Windows.Storage.StorageFile]::GetFileFromPathAsync($fullPath)
    $file = AwaitTask $fileOp ([Windows.Storage.StorageFile])

    $streamOp = $file.OpenAsync([Windows.Storage.FileAccessMode]::Read)
    $stream = AwaitTask $streamOp ([Windows.Storage.Streams.IRandomAccessStream])

    $decOp = [Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)
    $decoder = AwaitTask $decOp ([Windows.Graphics.Imaging.BitmapDecoder])

    $bmpOp = $decoder.GetSoftwareBitmapAsync()
    $bitmap = AwaitTask $bmpOp ([Windows.Graphics.Imaging.SoftwareBitmap])

    $ocr = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
    if (-not $ocr) {
        $lang = [Windows.Globalization.Language]::new("en-US")
        $ocr = [Windows.Media.Ocr.OcrEngine]::TryCreateFromLanguage($lang)
    }

    if (-not $ocr) {
        Write-Host "OCR_ERROR: No OCR language engine available"
        exit 1
    }

    $ocrResultOp = $ocr.RecognizeAsync($bitmap)
    $ocrResult = AwaitTask $ocrResultOp ([Windows.Media.Ocr.OcrResult])

    Write-Host "OCR_LINES_START"
    foreach ($line in $ocrResult.Lines) {
        Write-Host $line.Text
    }
    Write-Host "OCR_LINES_END"
} catch {
    Write-Host "OCR_ERROR: $_"
    exit 1
}
