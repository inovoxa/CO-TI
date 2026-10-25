<template>
  <div class="assistente flex gap-12px">
    <!-- conversas -->
    <ContentWrap class="lista w-260px shrink-0">
      <el-button class="mb-10px w-full" type="primary" @click="nova">
        <Icon class="mr-5px" icon="ep:plus" /> Nova conversa
      </el-button>
      <el-scrollbar max-height="calc(100vh - 260px)">
        <div
          v-for="s in sessoes"
          :key="s.id"
          :class="['sessao', { ativa: s.id === sessaoId }]"
          @click="abrir(s.id)"
        >
          <div class="truncate text-13px">{{ s.titulo }}</div>
          <div class="flex items-center justify-between">
            <span class="text-11px text-[var(--el-text-color-secondary)]">{{
              quando(s.atualizadaEm)
            }}</span>
            <el-button link size="small" type="danger" @click.stop="excluir(s)">
              <Icon icon="ep:delete" />
            </el-button>
          </div>
        </div>
        <el-empty v-if="!sessoes.length" :image-size="50" description="Nenhuma conversa ainda" />
      </el-scrollbar>
    </ContentWrap>

    <!-- conversa -->
    <ContentWrap class="conversa min-w-0 flex-1">
      <div ref="rolagem" class="mensagens">
        <div v-if="!mensagens.length" class="vazio">
          <Icon class="mb-8px" icon="ep:magic-stick" :size="36" />
          <div class="mb-4px text-16px font-bold">Pergunte sobre a TI da organização</div>
          <div class="mb-14px text-13px text-[var(--el-text-color-secondary)]">
            O assistente consulta inventário, chamados, base de conhecimento, licenças, pessoas,
            links e logs, só com o que você tem permissão para ver.
          </div>
          <div class="flex flex-wrap justify-center gap-8px">
            <el-button v-for="e in EXEMPLOS" :key="e" round size="small" @click="enviar(e)">{{
              e
            }}</el-button>
          </div>
        </div>
        <div v-for="m in mensagens" :key="m.id" :class="['msg', m.papel, { erro: m.erro }]">
          <div v-if="m.papel === 'user'" class="balao">{{ m.conteudo }}</div>
          <div v-else class="balao">
            <div v-dompurify-html="markdown(m.conteudo)" class="md"></div>
            <div v-if="m.consultas?.length" class="consultas">
              <Icon class="mr-4px" icon="ep:search" />Consultou:
              <el-tooltip
                v-for="(c, i) in m.consultas"
                :key="i"
                :content="JSON.stringify(c.argumentos)"
                placement="top"
              >
                <el-tag class="ml-4px" disable-transitions size="small" type="info">{{
                  CONSULTAS[c.ferramenta] ?? c.ferramenta
                }}</el-tag>
              </el-tooltip>
            </div>
          </div>
        </div>
        <div v-if="enviando" class="msg assistant">
          <div class="balao text-[var(--el-text-color-secondary)]">
            <Icon class="is-loading mr-6px" icon="ep:loading" />Consultando…
          </div>
        </div>
      </div>
      <div class="entrada">
        <el-input
          v-model="texto"
          :autosize="{ minRows: 1, maxRows: 6 }"
          :disabled="enviando"
          maxlength="4000"
          placeholder="Ex.: quantos computadores com Windows 10 temos na Saúde? (Enter envia, Shift+Enter quebra linha)"
          resize="none"
          type="textarea"
          @keydown.enter.exact.prevent="enviar()"
        />
        <el-button :disabled="!texto.trim()" :loading="enviando" type="primary" @click="enviar()">
          <Icon icon="ep:promotion" />
        </el-button>
      </div>
    </ContentWrap>
  </div>
</template>

<script lang="ts" setup>
import MarkdownIt from 'markdown-it'
import * as Api from '@/api/assistente'
import { CONSULTAS } from '@/api/assistente'

defineOptions({ name: 'AssistenteInterno' })
const message = useMessage()

const EXEMPLOS = [
  'Quantos computadores ainda usam Windows 10?',
  'Quais máquinas estão com o antivírus desatualizado?',
  'Resuma a fila do Service Desk',
  'Quais licenças estão com mais instalações que o comprado?',
  'Algum link fora do ar agora?'
]

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
const markdown = (t: string) => md.render(t ?? '')

const sessoes = ref<Api.Sessao[]>([])
const sessaoId = ref<number>()
const mensagens = ref<Api.Mensagem[]>([])
const texto = ref('')
const enviando = ref(false)
const rolagem = ref<HTMLElement>()

const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : ''

const descer = () =>
  nextTick(() => rolagem.value && (rolagem.value.scrollTop = rolagem.value.scrollHeight))

const carregarSessoes = async () => {
  sessoes.value = await Api.getSessoes()
}

const abrir = async (id: number) => {
  sessaoId.value = id
  mensagens.value = await Api.getMensagens(id)
  descer()
}

const nova = () => {
  sessaoId.value = undefined
  mensagens.value = []
  texto.value = ''
}

const enviar = async (pergunta?: string) => {
  const t = (pergunta ?? texto.value).trim()
  if (!t || enviando.value) return
  texto.value = ''
  mensagens.value.push({
    id: -Date.now(),
    papel: 'user',
    conteudo: t,
    criadaEm: new Date().toISOString()
  })
  enviando.value = true
  descer()
  try {
    const r = await Api.perguntar(t, sessaoId.value)
    const nova = sessaoId.value !== r.sessaoId
    sessaoId.value = r.sessaoId
    mensagens.value.push({
      id: -Date.now() - 1,
      papel: 'assistant',
      conteudo: r.resposta,
      erro: r.erro,
      consultas: r.consultas,
      criadaEm: new Date().toISOString()
    })
    if (nova) carregarSessoes()
  } catch {
    // a mensagem de erro já aparece pelo interceptador; devolve o texto para tentar de novo
    mensagens.value.pop()
    texto.value = t
  } finally {
    enviando.value = false
    descer()
  }
}

const excluir = async (s: Api.Sessao) => {
  await message.delConfirm(`Apagar a conversa "${s.titulo}"?`)
  await Api.excluirSessao(s.id)
  if (sessaoId.value === s.id) nova()
  await carregarSessoes()
}

onMounted(carregarSessoes)
</script>

<style lang="scss" scoped>
.assistente {
  height: calc(100vh - 150px);

  :deep(.el-card__body) {
    height: 100%;
  }
}

.lista :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
}

.sessao {
  padding: 6px 8px;
  margin-bottom: 4px;
  cursor: pointer;
  border-radius: 6px;

  &:hover {
    background: var(--el-fill-color-light);
  }

  &.ativa {
    background: var(--el-color-primary-light-9);
  }
}

.conversa :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
}

.mensagens {
  flex: 1;
  min-height: 0;
  padding: 4px 4px 12px;
  overflow-y: auto;
}

.vazio {
  display: flex;
  max-width: 620px;
  margin: 60px auto 0;
  text-align: center;
  flex-direction: column;
  align-items: center;
}

.msg {
  display: flex;
  margin-bottom: 12px;

  &.user {
    justify-content: flex-end;

    .balao {
      color: #fff;
      white-space: pre-wrap;
      background: var(--el-color-primary);
    }
  }

  &.assistant .balao {
    background: var(--el-fill-color-light);
  }

  &.erro .balao {
    color: var(--el-color-danger);
  }
}

.balao {
  max-width: 85%;
  padding: 8px 12px;
  font-size: 14px;
  line-height: 1.55;
  overflow-wrap: anywhere;
  border-radius: 10px;
}

.md {
  :deep(p) {
    margin: 0 0 6px;
  }

  :deep(table) {
    margin: 6px 0;
    border-collapse: collapse;
  }

  :deep(th),
  :deep(td) {
    padding: 3px 8px;
    border: 1px solid var(--el-border-color);
  }

  :deep(ul),
  :deep(ol) {
    padding-left: 20px;
    margin: 4px 0;
  }
}

.consultas {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.entrada {
  display: flex;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-lighter);
  gap: 8px;
  align-items: flex-end;
}
</style>
