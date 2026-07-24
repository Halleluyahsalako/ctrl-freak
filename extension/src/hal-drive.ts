// Minimal Drive REST calls needed for clip sync — upload a blob, fetch one
// back. Scope is drive.file (see hal-auth.ts), so this only ever touches
// files ctrl+freak itself created.

const HAL_DRIVE_UPLOAD_URL =
  "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart";
const HAL_DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files";

export async function halUploadFileToDrive(
  accessToken: string,
  blob: Blob,
  name: string,
): Promise<string> {
  const boundary = "hal_drive_boundary_" + crypto.randomUUID();
  const metadata = JSON.stringify({ name });

  const body = new Blob(
    [
      `--${boundary}\r\n`,
      "Content-Type: application/json; charset=UTF-8\r\n\r\n",
      metadata,
      `\r\n--${boundary}\r\n`,
      `Content-Type: ${blob.type || "application/octet-stream"}\r\n\r\n`,
      blob,
      `\r\n--${boundary}--`,
    ],
    { type: `multipart/related; boundary=${boundary}` },
  );

  const response = await fetch(HAL_DRIVE_UPLOAD_URL, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}` },
    body,
  });

  if (!response.ok) {
    throw new Error(`Drive upload failed: ${response.status} ${await response.text()}`);
  }

  const result = (await response.json()) as { id: string };
  return result.id;
}

export async function halFetchDriveFileBlob(
  accessToken: string,
  driveFileId: string,
): Promise<Blob> {
  const response = await fetch(
    `${HAL_DRIVE_FILES_URL}/${driveFileId}?alt=media`,
    { headers: { Authorization: `Bearer ${accessToken}` } },
  );
  if (!response.ok) {
    throw new Error(`Drive fetch failed: ${response.status} ${await response.text()}`);
  }
  return response.blob();
}
