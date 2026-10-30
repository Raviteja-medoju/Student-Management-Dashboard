import { HttpResponse } from '@angular/common/http';

/** Saves a blob response as a file, using the server's filename when it sends one. */
export function saveDownload(response: HttpResponse<Blob>, fallbackName: string): void {
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const match = /filename="?([^";]+)"?/i.exec(disposition);
  const name = match?.[1] ?? fallbackName;
  const url = URL.createObjectURL(response.body!);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
