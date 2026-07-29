const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const SIZE = 32;

// Simple bitmap letters (5x7), encoded as arrays of row bitmaps
const LETTERS = {
  'H': [0x11,0x11,0x11,0x1F,0x11,0x11,0x11],
  'S': [0x1E,0x01,0x01,0x0E,0x10,0x10,0x0F],
  'F': [0x1F,0x01,0x01,0x1F,0x01,0x01,0x01],
  'P': [0x1E,0x11,0x11,0x1E,0x01,0x01,0x01],
  '¥': [0x11,0x11,0x0A,0x04,0x04,0x0A,0x11]
};

function crc32(buf) {
  let crc = -1;
  for (let i = 0; i < buf.length; i++) {
    crc = crc ^ buf[i];
    for (let j = 0; j < 8; j++) {
      crc = (crc >>> 1) ^ ((crc & 1) ? 0xEDB88320 : 0);
    }
  }
  return (crc ^ -1) >>> 0;
}

function makeChunk(type, data) {
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, 'ascii');
  const crcInput = Buffer.concat([typeBuf, data]);
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(crcInput), 0);
  return Buffer.concat([length, typeBuf, data, crcBuf]);
}

function createIconWithLetter(letter, r, g, b) {
  const bitmap = LETTERS[letter] || LETTERS['?'];
  // Create a 32x32 RGB buffer
  const rawData = Buffer.alloc((SIZE * 3 + 1) * SIZE);

  // Calculate letter position to center it (5 wide, 7 tall, scale 3x = 15x21)
  const scale = 3;
  const letterW = 5 * scale; // 15
  const letterH = 7 * scale; // 21
  const offsetX = Math.floor((SIZE - letterW) / 2);  // 8
  const offsetY = Math.floor((SIZE - letterH) / 2);  // 5

  for (let y = 0; y < SIZE; y++) {
    rawData[y * (SIZE * 3 + 1)] = 0; // filter byte
    for (let x = 0; x < SIZE; x++) {
      const offset = y * (SIZE * 3 + 1) + 1 + x * 3;
      // Default: icon color (r,g,b)
      let pr = r, pg = g, pb = b;

      // Check if this pixel is part of the letter
      const lx = Math.floor((x - offsetX) / scale);
      const ly = Math.floor((y - offsetY) / scale);
      if (
        ly >= 0 && ly < 7 && lx >= 0 && lx < 5 &&
        (x - offsetX) >= 0 && (y - offsetY) >= 0
      ) {
        const rowBits = bitmap[ly];
        if (rowBits & (1 << (4 - lx))) {
          pr = 255; pg = 255; pb = 255; // white letter
        }
      }

      rawData[offset] = pr;
      rawData[offset + 1] = pg;
      rawData[offset + 2] = pb;
    }
  }

  const signature = Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
  const ihdrData = Buffer.alloc(13);
  ihdrData.writeUInt32BE(SIZE, 0);
  ihdrData.writeUInt32BE(SIZE, 4);
  ihdrData[8] = 8;   // bit depth
  ihdrData[9] = 2;   // RGB
  ihdrData[10] = 0;  // compression
  ihdrData[11] = 0;  // filter
  ihdrData[12] = 0;  // interlace

  return Buffer.concat([
    signature,
    makeChunk('IHDR', ihdrData),
    makeChunk('IDAT', zlib.deflateSync(rawData)),
    makeChunk('IEND', Buffer.alloc(0))
  ]);
}

const icons = {
  home:    { letter: 'H', inactive: [150,150,150], active: [30,136,229] },
  finance: { letter: '¥', inactive: [150,150,150], active: [30,136,229] },
  stock:   { letter: 'S', inactive: [150,150,150], active: [30,136,229] },
  fund:    { letter: 'F', inactive: [150,150,150], active: [30,136,229] },
  profile: { letter: 'P', inactive: [150,150,150], active: [30,136,229] }
};

const imagesDir = path.join(__dirname, '..', 'images');
if (!fs.existsSync(imagesDir)) fs.mkdirSync(imagesDir, { recursive: true });

console.log('Generating named TabBar icons...');
for (const [name, info] of Object.entries(icons)) {
  const inactivePng = createIconWithLetter(info.letter, ...info.inactive);
  const activePng = createIconWithLetter(info.letter, ...info.active);
  fs.writeFileSync(path.join(imagesDir, `${name}.png`), inactivePng);
  fs.writeFileSync(path.join(imagesDir, `${name}-active.png`), activePng);
  console.log(`  ${name}.png [${info.letter}]: ${inactivePng.length}B (inactive) / ${activePng.length}B (active)`);
}
