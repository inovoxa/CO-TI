import { useTimeAgo as useTimeAgoCore, UseTimeAgoMessages } from '@vueuse/core'
import { useLocaleStoreWithOut } from '@/store/modules/locale'

const TIME_AGO_MESSAGE_MAP: {
  'pt-BR': UseTimeAgoMessages
  en: UseTimeAgoMessages
} = {
  // @ts-ignore
  'pt-BR': {
    justNow: 'agora mesmo',
    past: (n) => (n.match(/\d/) ? `há ${n}` : n),
    future: (n) => (n.match(/\d/) ? `em ${n}` : n),
    month: (n, past) => (n === 1 ? (past ? 'mês passado' : 'próximo mês') : `${n} meses`),
    year: (n, past) => (n === 1 ? (past ? 'ano passado' : 'próximo ano') : `${n} anos`),
    day: (n, past) => (n === 1 ? (past ? 'ontem' : 'amanhã') : `${n} dias`),
    week: (n, past) => (n === 1 ? (past ? 'semana passada' : 'próxima semana') : `${n} semanas`),
    hour: (n) => `${n} horas`,
    minute: (n) => `${n} minutos`,
    second: (n) => `${n} segundos`
  },
  // @ts-ignore
  en: {
    justNow: 'just now',
    past: (n) => (n.match(/\d/) ? `${n} ago` : n),
    future: (n) => (n.match(/\d/) ? `in ${n}` : n),
    month: (n, past) =>
      n === 1 ? (past ? 'last month' : 'next month') : `${n} month${n > 1 ? 's' : ''}`,
    year: (n, past) =>
      n === 1 ? (past ? 'last year' : 'next year') : `${n} year${n > 1 ? 's' : ''}`,
    day: (n, past) => (n === 1 ? (past ? 'yesterday' : 'tomorrow') : `${n} day${n > 1 ? 's' : ''}`),
    week: (n, past) =>
      n === 1 ? (past ? 'last week' : 'next week') : `${n} week${n > 1 ? 's' : ''}`,
    hour: (n) => `${n} hour${n > 1 ? 's' : ''}`,
    minute: (n) => `${n} minute${n > 1 ? 's' : ''}`,
    second: (n) => `${n} second${n > 1 ? 's' : ''}`
  }
}

export const useTimeAgo = (time: Date | number | string) => {
  const localeStore = useLocaleStoreWithOut()

  const currentLocale = computed(() => localeStore.getCurrentLocale)

  const timeAgo = useTimeAgoCore(time, {
    messages: TIME_AGO_MESSAGE_MAP[unref(currentLocale).lang]
  })

  return timeAgo
}
