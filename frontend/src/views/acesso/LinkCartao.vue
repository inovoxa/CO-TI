<template>
  <div class="cartao">
    <a :href="seguro" :title="link.url" class="corpo" rel="noopener noreferrer" target="_blank">
      <span class="letra">{{ link.titulo.charAt(0).toUpperCase() }}</span>
      <span class="min-w-0">
        <span class="nome">{{ link.titulo }}</span>
        <span class="descricao">{{ link.descricao || dominio }}</span>
      </span>
    </a>
    <span v-if="editavel" class="acoes">
      <el-button link title="Editar" @click="$emit('editar')"><Icon icon="ep:edit" /></el-button>
      <el-button link title="Excluir" type="danger" @click="$emit('excluir')"><Icon icon="ep:delete" /></el-button>
    </span>
  </div>
</template>

<script lang="ts" setup>
import type { Link } from '@/api/acesso'

const props = defineProps<{ link: Link; editavel?: boolean }>()
defineEmits(['editar', 'excluir'])

/** O servidor já recusa o que não for http ou https; aqui é a segunda barreira, para o que estiver no banco. */
const seguro = computed(() => (/^https?:\/\//i.test(props.link.url) ? props.link.url : undefined))
const dominio = computed(() => {
  try {
    return new URL(props.link.url).host
  } catch {
    return props.link.url
  }
})
</script>

<style scoped>
.cartao {
  display: flex;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  align-items: center;
}

.cartao:hover {
  border-color: var(--el-color-primary);
}

.corpo {
  display: flex;
  min-width: 0;
  padding: 10px;
  color: inherit;
  text-decoration: none;
  align-items: center;
  flex: 1;
  gap: 10px;
}

.letra {
  display: flex;
  width: 34px;
  height: 34px;
  font-weight: 700;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-radius: 6px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
}

.nome,
.descricao {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nome {
  font-weight: 600;
}

.descricao {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.acoes {
  display: flex;
  padding-right: 8px;
  flex-shrink: 0;
}

.acoes :deep(.el-button + .el-button) {
  margin-left: 4px;
}
</style>
