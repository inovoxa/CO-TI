import { defineStore } from 'pinia'
import { store } from '../index'
import ptBr from 'element-plus/es/locale/lang/pt-br'
import en from 'element-plus/es/locale/lang/en'
import { CACHE_KEY, useCache } from '@/hooks/web/useCache'
import { LocaleDropdownType } from '@/types/localeDropdown'

const { wsCache } = useCache()

const elLocaleMap = {
  'pt-BR': ptBr,
  en: en
}

// CO-TI: idioma padrão português; um valor antigo em cache (ex.: zh-CN) volta para o padrão
const IDIOMA_PADRAO = 'pt-BR'
const idiomaSalvo = (): string => {
  const l = wsCache.get(CACHE_KEY.LANG)
  return l && elLocaleMap[l] ? l : IDIOMA_PADRAO
}
interface LocaleState {
  currentLocale: LocaleDropdownType
  localeMap: LocaleDropdownType[]
}

export const useLocaleStore = defineStore('locales', {
  state: (): LocaleState => {
    return {
      currentLocale: {
        lang: idiomaSalvo(),
        elLocale: elLocaleMap[idiomaSalvo()]
      },
      // 多语言
      localeMap: [
        {
          lang: 'pt-BR',
          name: 'Português (Brasil)'
        },
        {
          lang: 'en',
          name: 'English'
        }
      ]
    }
  },
  getters: {
    getCurrentLocale(): LocaleDropdownType {
      return this.currentLocale
    },
    getLocaleMap(): LocaleDropdownType[] {
      return this.localeMap
    }
  },
  actions: {
    setCurrentLocale(localeMap: LocaleDropdownType) {
      // this.locale = Object.assign(this.locale, localeMap)
      this.currentLocale.lang = localeMap?.lang
      this.currentLocale.elLocale = elLocaleMap[localeMap?.lang]
      wsCache.set(CACHE_KEY.LANG, localeMap?.lang)
    }
  }
})

export const useLocaleStoreWithOut = () => {
  return useLocaleStore(store)
}
