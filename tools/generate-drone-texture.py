"""Generate the opaque 64x32 base material matching DroneEntityModel's UV map."""
from pathlib import Path
import struct
import zlib


def chunk(kind, payload):
    return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind + payload) & 0xffffffff)


def main():
    width, height = 64, 32
    scanlines = bytearray()
    for y in range(height):
        scanlines.append(0)
        for x in range(width):
            color = (100, 111, 125, 255)
            if 16 <= y < 21:
                color = (47, 55, 66, 255)
            elif y >= 21:
                color = (28, 34, 43, 255)
            elif x >= 32:
                color = (231, 115, 43, 255) if y < 8 else (29, 37, 46, 255)
            scanlines.extend(color)
    image = b'\x89PNG\r\n\x1a\n'
    image += chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
    image += chunk(b'IDAT', zlib.compress(bytes(scanlines), 9))
    image += chunk(b'IEND', b'')
    output = Path(__file__).resolve().parent.parent / 'fabric-mod/src/main/resources/assets/fpvdrone/textures/entity/drone.png'
    output.write_bytes(image)
    print(f'{output}: {width}x{height}, {len(image)} bytes')


if __name__ == '__main__':
    main()
