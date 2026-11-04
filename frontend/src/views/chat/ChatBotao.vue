<template>
  <el-badge :hidden="!naoLidas" :max="99" :value="naoLidas" class="chat-botao">
    <div title="Chat" @click="router.push('/chat')">
      <Icon color="var(--top-header-text-color)" icon="ep:chat-dot-round" :size="18" />
    </div>
  </el-badge>
</template>

<script lang="ts" setup>
import { iniciarChat, useChat } from './useChat'

/** Botão do cabeçalho: mantém o contador e os avisos do chat em qualquer tela. */
const router = useRouter()
const { naoLidas } = useChat()
onMounted(iniciarChat)
</script>

<style scoped>
.chat-botao {
  display: flex;
  height: 100%;
  cursor: pointer;
  align-items: center;
}

.chat-botao :deep(.el-badge__content) {
  top: 12px;
}
</style>
