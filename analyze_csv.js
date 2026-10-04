const fs = require('fs');
const content = fs.readFileSync('medicarex_inventory_12000.csv', 'utf8').trim();
const lines = content.split(/\r?\n/);
console.log('Total lines:', lines.length);
console.log('Header:', lines[0]);

const medicineMap = new Map();
let emptyRows = 0;

for (let i = 1; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) {
        emptyRows++;
        continue;
    }
    // Simple CSV parse
    const match = line.match(/^"([^"]+)"|([^,]+)/);
    const medName = match ? (match[1] || match[2]).trim() : '';
    if (medName) {
        medicineMap.set(medName.toLowerCase(), (medicineMap.get(medName.toLowerCase()) || 0) + 1);
    }
}

console.log('Total valid data rows:', lines.length - 1 - emptyRows);
console.log('Unique medicine names (case-insensitive):', medicineMap.size);
console.log('Sample medicines with occurrence count:');
let count = 0;
for (const [name, occurrences] of medicineMap.entries()) {
    console.log(`  - "${name}": ${occurrences} occurrences`);
    count++;
    if (count >= 15) break;
}
