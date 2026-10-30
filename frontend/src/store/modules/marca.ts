import { store } from '@/store'
import { defineStore } from 'pinia'
import { getMarca, type Marca } from '@/api/system/marca'
import { useAppStore } from '@/store/modules/app'
import logoPadrao from '@/assets/imgs/coti.svg'
import fundoPadrao from '@/assets/imgs/login-fundo.jpg'

// a tela de carregamento (index.html) lê daqui antes do Vue subir
const CHAVE = 'coti-marca'
export const FRASE_PADRAO = 'Monitoramento da rede, atendimento e chamados de TI em um só lugar.'

const guardada = (): Partial<Marca> => {
  try {
    return JSON.parse(localStorage.getItem(CHAVE) || '{}') || {}
  } catch {
    return {}
  }
}

export const useMarcaStore = defineStore('coti-marca', {
  state: () => ({
    logo: guardada().logo || '',
    fundo: guardada().fundo || '',
    frase: guardada().frase || ''
  }),
  getters: {
    /** a logo enviada pela instância ou, sem ela, a do CO-TI */
    logoUrl: (s) => s.logo || logoPadrao,
    /** a foto do login enviada pela instância ou, sem ela, a padrão */
    fundoUrl: (s) => s.fundo || fundoPadrao,
    /** o texto do login da instância ou, sem ele, o padrão */
    fraseLogin: (s) => s.frase || FRASE_PADRAO
  },
  actions: {
    async carregar() {
      this.aplicar(guardada())
      try {
        this.aplicar(await getMarca())
      } catch {
        // sem backend (ou fora do ar) fica a última conhecida
      }
    },
    aplicar(marca: Partial<Marca>) {
      this.logo = marca.logo || ''
      this.fundo = marca.fundo || ''
      this.frase = marca.frase || ''
      const app = useAppStore()
      if (marca.nome && marca.nome !== app.getTitle) {
        // a aba já pode estar com "<nome antigo> - <página>"
        if (document.title.startsWith(app.getTitle))
          document.title = marca.nome + document.title.slice(app.getTitle.length)
        app.setTitle(marca.nome)
      }
      const icone = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
      if (icone) {
        icone.href = this.logo || '/favicon.svg'
        icone.type = this.logo ? '' : 'image/svg+xml'
      }
      try {
        localStorage.setItem(
          CHAVE,
          JSON.stringify({
            nome: app.getTitle,
            logo: this.logo,
            fundo: this.fundo,
            frase: this.frase
          })
        )
      } catch {
        // navegação privada
      }
    }
  }
})

export const useMarcaStoreWithOut = () => useMarcaStore(store)
