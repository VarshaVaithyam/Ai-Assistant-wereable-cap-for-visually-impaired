export function normalizeHost(value) {
  const url = new URL(value.trim());
  if (!['http:', 'https:'].includes(url.protocol) || !url.hostname ||
      url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    throw new Error('Enter a base HTTP or HTTPS address without a path.');
  }
  return url.origin;
}
export function readSettings(storage) {
  try {
    const data = JSON.parse(storage.getItem('visionassist-visual-preview') || '{}');
    return { host: normalizeHost(data.host || 'http://192.168.4.1'), audio: data.audio !== false };
  } catch {
    return { host: 'http://192.168.4.1', audio: true };
  }
}
