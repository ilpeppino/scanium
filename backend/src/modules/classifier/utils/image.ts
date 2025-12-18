import sharp from 'sharp';

const SUPPORTED_TYPES = new Set(['image/jpeg', 'image/jpg', 'image/png', 'image/webp']);

export function isSupportedImage(contentType?: string | null): boolean {
  if (!contentType) return false;
  return SUPPORTED_TYPES.has(contentType.toLowerCase());
}

/**
 * Re-encodes the image in-memory to strip EXIF/metadata.
 * Images are never written to disk (retainUploads=false by default).
 */
export async function sanitizeImageBuffer(
  buffer: Buffer,
  contentType: string
): Promise<{ buffer: Buffer; normalizedType: string }> {
  if (contentType.includes('png')) {
    const processed = await sharp(buffer, { failOnError: true })
      .png({ compressionLevel: 9 })
      .toBuffer();
    return { buffer: processed, normalizedType: 'image/png' };
  }

  if (contentType.includes('webp')) {
    const processed = await sharp(buffer, { failOnError: true })
      .webp({ quality: 90 })
      .toBuffer();
    return { buffer: processed, normalizedType: 'image/webp' };
  }

  const processed = await sharp(buffer, { failOnError: true })
    .jpeg({ quality: 90, mozjpeg: true })
    .toBuffer();
  return { buffer: processed, normalizedType: 'image/jpeg' };
}
