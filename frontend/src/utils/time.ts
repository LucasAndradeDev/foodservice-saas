export function minutesSince(dateString: string) {
  return Math.floor((Date.now() - new Date(dateString).getTime()) / 60000)
}
