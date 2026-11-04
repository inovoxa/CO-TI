<template>
  <el-popover v-model:visible="visivel" placement="top-start" trigger="click" :width="300">
    <template #reference>
      <el-button :disabled="desabilitado" title="Emojis" @mousedown.prevent>😀</el-button>
    </template>
    <div v-for="g in GRUPOS" :key="g.nome" class="mb-6px">
      <div class="text-12px text-[var(--el-text-color-secondary)]">{{ g.nome }}</div>
      <div class="grade">
        <button v-for="e in g.emojis" :key="e" class="emoji" type="button" @mousedown.prevent="escolher(e)">{{ e }}</button>
      </div>
    </div>
  </el-popover>
</template>

<script lang="ts" setup>
/** Emojis do próprio sistema, sem imagens nem serviço externo. */
defineProps<{ desabilitado?: boolean }>()
const emit = defineEmits<{ escolher: [emoji: string] }>()
const visivel = ref(false)

const GRUPOS = [
  { nome: 'Rostos', emojis: ['😀', '😄', '😂', '🙂', '😉', '😊', '😍', '😎', '🤔', '😐', '😅', '😢', '😡', '😴', '🤝', '🙏'] },
  { nome: 'Gestos', emojis: ['👍', '👎', '👏', '🙌', '👋', '✌️', '👌', '💪'] },
  { nome: 'Trabalho', emojis: ['✅', '❌', '⚠️', '❗', '❓', '📌', '📎', '📅', '⏰', '📞', '💻', '🖨️', '🔧', '🔒', '💡', '🚀'] },
  { nome: 'Outros', emojis: ['🎉', '🎂', '❤️', '⭐', '🔥', '☕', '🌴', '✈️'] }
]
const escolher = (e: string) => {
  emit('escolher', e)
  visivel.value = false
}
</script>

<style scoped>
.grade {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
}

.emoji {
  padding: 2px;
  font-size: 18px;
  cursor: pointer;
  background: none;
  border: 0;
  border-radius: 4px;
}

.emoji:hover {
  background: var(--el-fill-color);
}
</style>
