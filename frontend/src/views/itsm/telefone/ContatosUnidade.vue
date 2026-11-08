<!-- Contatos de uma unidade (mapa do monitoramento e detalhe do chamado), com os de emergência primeiro. -->
<template>
  <div v-if="dados" class="contatos">
    <div class="contatos__titulo">Contatos de {{ dados.unidade }}</div>
    <div v-if="dados.linkFora" class="contatos__aviso">
      Link fora: ramal pode não funcionar. Tente o celular ou o WhatsApp.
    </div>
    <div v-if="!dados.contatos.length" class="contatos__vazio">Nenhum contato cadastrado.</div>
    <div v-for="c in dados.contatos" :key="c.id" class="contatos__item">
      <div class="contatos__nome">
        {{ c.nome }}
        <span v-if="c.emergencia" class="contatos__emergencia">Emergência</span>
      </div>
      <ContatoAcoes :link-fora="dados.linkFora" :t="c" />
    </div>
  </div>
</template>

<script lang="ts" setup>
import { checkPermi } from '@/utils/permission'
import * as Api from '@/api/itsm/telefone'
import ContatoAcoes from './ContatoAcoes.vue'

const props = defineProps<{ unidadeId?: number; deptId?: number; linkFora?: boolean }>()
const emit = defineEmits<{ (e: 'carregado'): void }>()

const dados = ref<Api.ContatosUnidade | null>(null)

let pedido = 0
const carregar = async () => {
  const meu = ++pedido
  dados.value = null
  // quem não vê a lista não vê este bloco (e não recebe erro de permissão)
  if ((!props.unidadeId && !props.deptId) || !checkPermi(['itsm:telefone:query'])) return
  const r = await Api.getContatosUnidade({ unidadeId: props.unidadeId, deptId: props.deptId })
  if (meu !== pedido) return
  dados.value = r
  emit('carregado')
}

// o link pode cair com o bloco aberto (o mapa recebe o estado pelo WebSocket)
watch(() => [props.unidadeId, props.deptId, props.linkFora], carregar, { immediate: true })
</script>

<style scoped>
.contatos__titulo {
  font-size: 13px;
  font-weight: 700;
}

.contatos__aviso {
  padding: 4px 8px;
  margin-top: 6px;
  font-size: 12px;
  color: #b91c1c;
  background: #fef2f2;
  border-radius: 4px;
}

.contatos__vazio {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.contatos__item {
  padding-top: 6px;
  margin-top: 6px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.contatos__nome {
  font-size: 12.5px;
  font-weight: 600;
}

.contatos__emergencia {
  padding: 0 6px;
  margin-left: 4px;
  font-size: 11px;
  font-weight: 600;
  color: #b91c1c;
  background: #fef2f2;
  border-radius: 999px;
}
</style>
