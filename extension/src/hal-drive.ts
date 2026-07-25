// Minimal Drive REST calls needed for clip sync — upload a blob, fetch one
// back. Scope is drive.file (see hal-auth.ts), so this only ever touches
// files ctrl+freak itself created.

const HAL_DRIVE_UPLOAD_URL =
  "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart";
const HAL_DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files";
const HAL_DRIVE_FOLDER_NAME = "Ctrl+Freak";
const HAL_DRIVE_FOLDER_MIME = "application/vnd.google-apps.folder";

// Cached per popup/tab lifetime — cheap enough to refetch if it's ever wrong,
// no need to persist across reloads.
let halFolderIdCache: string | null = null;

// Everything used to land loose in the root of My Drive with no way to find
// it again. Every upload now goes into one "Ctrl+Freak" folder instead.
async function halGetOrCreateAppFolder(accessToken: string): Promise<string> {
  if (halFolderIdCache) return halFolderIdCache;

  const query = encodeURIComponent(
    `name = '${HAL_DRIVE_FOLDER_NAME}' and mimeType = '${HAL_DRIVE_FOLDER_MIME}' and trashed = false`,
  );
  const searchResponse = await fetch(
    `${HAL_DRIVE_FILES_URL}?q=${query}&spaces=drive&fields=files(id)`,
    { headers: { Authorization: `Bearer ${accessToken}` } },
  );
  if (searchResponse.ok) {
    const data = (await searchResponse.json()) as { files: { id: string }[] };
    if (data.files.length > 0) {
      halFolderIdCache = data.files[0].id;
      return halFolderIdCache;
    }
  }

  const createResponse = await fetch(HAL_DRIVE_FILES_URL, {
    method: "POST",
    headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
    body: JSON.stringify({ name: HAL_DRIVE_FOLDER_NAME, mimeType: HAL_DRIVE_FOLDER_MIME }),
  });
  if (!createResponse.ok) {
    throw new Error(`Drive folder create failed: ${createResponse.status} ${await createResponse.text()}`);
  }
  const created = (await createResponse.json()) as { id: string };
  halFolderIdCache = created.id;
  return halFolderIdCache;
}

export async function halUploadFileToDrive(
  accessToken: string,
  blob: Blob,
  name: string,
): Promise<string> {
  const folderId = await halGetOrCreateAppFolder(accessToken);
  const boundary = "hal_drive_boundary_" + crypto.randomUUID();
  const metadata = JSON.stringify({ name, parents: [folderId] });

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
