package com.squareup.picasso;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.IOException;

/**
 * Internal class from https://github.com/JohnWowUs/picasso
 * See https://github.com/square/picasso/pull/1031#issuecomment-117101259
 */
public final class ExifStreamReader {
    private static final String TAG = "CameraExif";

    // Returns the orientation value
    public static int getOrientation(@NonNull MarkableInputStream stream) throws IOException {
        long mark = stream.savePosition(65536);
        int orientation = getOrientation(stream, 65536);
        stream.reset(mark);
        return orientation;
    }

    // Returns the orientation value
    private static int getOrientation(@NonNull MarkableInputStream jpegstream, int byteLimit) throws IOException {
        int marker;
        int offset = 0;
        int length = 0;

        // ISO/IEC 10918-1:1993(E)
        while ((offset + 3 < byteLimit) && (jpegstream.read() & 0xFF) == 0xFF) {
            marker = jpegstream.read() & 0xFF;
            offset += 2;
            // Check if the marker is a padding byte
            if (marker == 0xFF) {
                continue;
            }

            // Check if the marker is SOI or TEM.
            if (marker == 0xD8 || marker == 0x01) {
                continue;
            }
            // Check if the marker is EOI or SOS.
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }

            // Get the length and check if it is reasonable.
            length = pack(jpegstream, 2, false);
            if (length < 2 || offset + length > byteLimit) {
                Log.e(TAG, "Invalid length");
                return 0;
            }

            // Break if the marker is EXIF in APP1.
            int marker2 = 0x45786966;
            if (marker == 0xE1 && length >= 8
                && (marker2 = pack(jpegstream, 4, false)) == 0x45786966
                && pack(jpegstream, 2, false) == 0) {
                Log.e(TAG, "APP1");
                offset += 8;
                length -= 8;
                break;
            }
            // Skip other markers.
            offset += length;
            if (marker != 0xE1 || length < 8) {
                //noinspection ResultOfMethodCallIgnored
                jpegstream.skip(length - 2);
            } else if (marker2 != 0x45786966) {
                //noinspection ResultOfMethodCallIgnored
                jpegstream.skip(length - 6);
            } else {
                //noinspection ResultOfMethodCallIgnored
                jpegstream.skip(length - 8);
            }
            length = 0;
        }

        // JEITA CP-3451 Exif Version 2.2
        if (length > 8) {
            // Identify the byte order.
            int tag = pack(jpegstream, 4, false);
            if (tag != 0x49492A00 && tag != 0x4D4D002A) {
                Log.e(TAG, "Invalid byte order");
                return 0;
            }
            boolean littleEndian = (tag == 0x49492A00);
            // Get the offset and check if it is reasonable.
            int count = pack(jpegstream, 4, littleEndian) + 2;
            if (count < 10 || count > length) {
                Log.e(TAG, "Invalid offset " + count + " , " + length);
                return 0;
            }
            offset += count;
            length -= count;
            //noinspection ResultOfMethodCallIgnored
            jpegstream.skip(count - 10);
            // Get the count and go through all the elements.
            count = pack(jpegstream, 2, littleEndian);
            while (count-- > 0 && length >= 12) {
                // Get the tag and check if it is orientation.
                tag = pack(jpegstream, 2, littleEndian);
                if (tag == 0x0112) {
                    //noinspection ResultOfMethodCallIgnored
                    jpegstream.skip(6);
                    int orientation = pack(jpegstream, 2, littleEndian);
                    switch (orientation) {
                        case 0:
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                        case 6:
                        case 7:
                        case 8:
                            return orientation;
                        default:
                            break;
                    }
                    Log.i(TAG, "Unsupported orientation");
                    return 0;
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    jpegstream.skip(10);
                }
                offset += 12;
                length -= 12;
            }
        }

        return 0;
    }

    private static int pack(MarkableInputStream jpegstream, int length, boolean littleEndian)
        throws IOException {
        int shiftL = 0, shiftB = 8;
        if (littleEndian) {
            shiftB = 0;
            shiftL = 1;
        }
        int value = 0;
        for (int i = 0; i < length; i++) {
            value = ((value << shiftB) | ((jpegstream.read() & 0xFF) << (shiftL * i * 8)));
        }
        return value;
    }

    private ExifStreamReader() {}
}

