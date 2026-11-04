<template>
  <div v-if="m.tipo === 'sistema'" class="sistema">{{ m.conteudo }}</div>
  <div v-else class="linha" :class="{ minha }" :data-seq="m.seq">
    <el-avatar v-if="!minha" :size="30" :src="m.avatar">{{ inicial }}</el-avatar>
    <div class="balao" :class="{ falhou: m.falhou }">
      <div v-if="m.tipo === 'auto'" class="automatica"><Icon class="mr-2px" icon="ep:timer" />Resposta automática</div>
      <div v-else-if="!minha && grupo" class="autor">{{ m.remetente }}</div>
      <div v-if="m.resposta" class="citacao">
        <b>{{ m.resposta.remetente }}</b>
        <div>{{ m.resposta.trecho }}</div>
      </div>
      <i v-if="m.apagada" class="apagada">Mensagem apagada</i>
      <template v-else>
        <div v-if="m.conteudo" class="texto">
          <template v-for="(p, i) in partes" :key="i">
            <a v-if="p.tipo === 'link'" :href="p.texto" rel="noopener noreferrer" target="_blank">{{ p.texto }}</a>
            <b v-else-if="p.tipo === 'mencao'" class="mencao">{{ p.texto }}</b>
            <template v-else>{{ p.texto }}</template>
          </template>
        </div>
        <div v-for="a in m.anexos" :key="a.id" class="anexo">
          <img
            v-if="a.imagem && imagens[a.id]"
            :alt="a.nome"
            :src="imagens[a.id]"
            class="imagem"
            @click="baixar(a)"
          />
          <el-link v-else @click="baixar(a)">
            <Icon class="mr-4px" icon="ep:document" />{{ a.nome }} ({{ tamanho(a.tamanho) }})
          </el-link>
        </div>
      </template>
      <div class="rodape">
        <span v-if="m.falhou" class="erro">Não enviada.</span>
        <el-link v-if="m.falhou" type="danger" @click="$emit('reenviar', m)">Tentar de novo</el-link>
        <span v-else-if="m.enviando">Enviando…</span>
        <template v-else>
          <span v-if="m.editada && !m.apagada">editada · </span>{{ hora(m.criadaEm) }}
        </template>
      </div>
    </div>
    <el-dropdown v-if="m.tipo === 'texto' && !m.apagada && !m.enviando && !m.falhou" class="acoes" trigger="click" @command="(c: string) => $emit(c as any, m)">
      <el-button link><Icon icon="ep:more-filled" /></el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="responder">Responder</el-dropdown-item>
          <el-dropdown-item v-if="minha && m.conteudo" command="editar">Editar</el-dropdown-item>
          <el-dropdown-item v-if="minha || admin" command="apagar">Apagar para todos</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script lang="ts" setup>
import download from '@/utils/download'
import * as Api from '@/api/chat'

/** Tudo vira texto escapado pelo Vue, sem v-html: mensagem não executa nada na tela de quem lê. */
const props = defineProps<{ m: any; minha: boolean; grupo: boolean; admin: boolean }>()
defineEmits(['responder', 'editar', 'apagar', 'reenviar'])

const inicial = computed(() => (props.m.remetente || '?').charAt(0).toUpperCase())
const hora = (t: number) => new Date(t).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
const tamanho = (b: number) =>
  b < 1024 * 1024 ? `${Math.max(1, Math.round(b / 1024))} KB` : `${(b / 1024 / 1024).toFixed(1)} MB`

const escaparRegex = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
const partes = computed(() => {
  const texto: string = props.m.conteudo || ''
  const nomes = (props.m.mencoes || []).filter((p: any) => p.nome).map((p: any) => `@${escaparRegex(p.nome)}`)
  const padrao = new RegExp(`(https?://[^\\s<>"]+)${nomes.length ? '|(' + nomes.join('|') + ')' : ''}`, 'g')
  const r: { tipo: string; texto: string }[] = []
  let ult = 0
  for (const a of texto.matchAll(padrao)) {
    if (a.index! > ult) r.push({ tipo: 'texto', texto: texto.slice(ult, a.index) })
    r.push({ tipo: a[1] ? 'link' : 'mencao', texto: a[0] })
    ult = a.index! + a[0].length
  }
  if (ult < texto.length) r.push({ tipo: 'texto', texto: texto.slice(ult) })
  return r
})

// a imagem precisa do login: vem pela API e vira um endereço local do navegador
const imagens = reactive<Record<number, string>>({})
const carregar = async () => {
  for (const a of props.m.anexos || []) {
    if (a.imagem && !imagens[a.id] && a.tamanho < 10 * 1024 * 1024) {
      const b: any = await Api.baixarAnexo(a.id).catch(() => null)
      const blob = b instanceof Blob ? b : b?.data
      if (blob) imagens[a.id] = URL.createObjectURL(new Blob([blob], { type: tipoImagem(a.nome) }))
    }
  }
}
const tipoImagem = (nome: string) => {
  const e = nome.split('.').pop()?.toLowerCase()
  return e === 'png' ? 'image/png' : e === 'gif' ? 'image/gif' : e === 'webp' ? 'image/webp' : 'image/jpeg'
}
watch(() => props.m.anexos, carregar, { immediate: true })
onBeforeUnmount(() => Object.values(imagens).forEach((u) => URL.revokeObjectURL(u)))

const baixar = async (a: Api.Anexo) => {
  const b: any = await Api.baixarAnexo(a.id)
  const blob = b instanceof Blob ? b : b?.data
  download.excel(blob, a.nome)
}
</script>

<style scoped>
.linha {
  display: flex;
  margin: 6px 0;
  align-items: flex-end;
  gap: 8px;
}

.linha.minha {
  flex-direction: row-reverse;
}

.balao {
  max-width: min(70%, 640px);
  padding: 6px 10px;
  background: var(--el-fill-color);
  border-radius: 10px;
}

.minha .balao {
  background: var(--el-color-primary-light-8);
}

.balao.falhou {
  outline: 1px solid var(--el-color-danger);
}

.automatica {
  margin-bottom: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.autor {
  margin-bottom: 2px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.texto {
  word-break: break-word;
  white-space: pre-wrap;
}

.mencao {
  color: var(--el-color-primary);
}

.citacao {
  padding: 2px 8px;
  margin-bottom: 4px;
  font-size: 12px;
  border-left: 3px solid var(--el-color-primary);
  opacity: 0.85;
}

.apagada {
  color: var(--el-text-color-secondary);
}

.rodape {
  margin-top: 2px;
  font-size: 11px;
  text-align: right;
  color: var(--el-text-color-secondary);
}

.erro {
  margin-right: 4px;
  color: var(--el-color-danger);
}

.imagem {
  display: block;
  max-width: 280px;
  max-height: 280px;
  margin-top: 4px;
  cursor: pointer;
  border-radius: 6px;
}

.acoes {
  visibility: hidden;
}

.linha:hover .acoes {
  visibility: visible;
}

.sistema {
  margin: 8px 0;
  font-size: 12px;
  text-align: center;
  color: var(--el-text-color-secondary);
}
</style>
