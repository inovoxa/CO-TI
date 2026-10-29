import { ref } from 'vue'

/** Janela flutuante do assistente: aberta pelo ícone da barra superior, fica no canto inferior direito. */
export const assistenteAberto = ref(false)

export const alternarAssistente = () => {
  assistenteAberto.value = !assistenteAberto.value
}
