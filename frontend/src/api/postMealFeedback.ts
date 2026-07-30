import { http } from './http'

export interface PostMealFeedbackContext {
  restaurantName: string
  logo: string | null
  tableNumbers: number[]
  alreadySubmitted: boolean
}

export function getFeedbackContext(slug: string, tabId: string) {
  return http.get<PostMealFeedbackContext>(`/public/menu/${slug}/tabs/${tabId}/feedback`).then((res) => res.data)
}

export function submitFeedback(slug: string, tabId: string, payload: { rating: number; comment?: string }) {
  return http
    .post<PostMealFeedbackContext>(`/public/menu/${slug}/tabs/${tabId}/feedback`, payload)
    .then((res) => res.data)
}
