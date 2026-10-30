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

// CO-TI: só português do Brasil; um idioma antigo em cache (en, zh-CN) é ignorado
const IDIOMA_PADRAO: LocaleType = 'pt-BR'
const idiomaSalvo = (): LocaleType => IDIOMA_PADRAO
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
