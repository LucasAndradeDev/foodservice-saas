import axios from 'axios'

export interface CepAddress {
  street: string
  neighborhood: string
  city: string
}

interface ViaCepResponse {
  logradouro: string
  bairro: string
  localidade: string
  erro?: boolean
}

// Public Brazilian postal-code lookup, unrelated to our own API - called directly with plain
// axios (not the `http` instance) since it doesn't share our baseURL or auth headers.
export async function lookupCep(cep: string): Promise<CepAddress | null> {
  const digits = cep.replace(/\D/g, '')
  if (digits.length !== 8) return null

  const { data } = await axios.get<ViaCepResponse>(`https://viacep.com.br/ws/${digits}/json/`)
  if (data.erro) return null

  return {
    street: data.logradouro,
    neighborhood: data.bairro,
    city: data.localidade,
  }
}
