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
            O assistente consulta inventário, chamados, problemas, mudanças, conversas, base de
            conhecimento, licenças, pessoas, links e logs, só com o que você tem permissão para ver.
          </div>
          <div class="flex flex-wrap justify-center gap-8px">
            <el-button v-for="e in EXEMPLOS" :key="e" round size="small" @click="enviar(e)">{{
              e
            }}</el-button>
          </div>
        </div>
        <div v-for="(m, i) in mensagens" :key="m.id" :class="['msg', m.papel, { erro: m.erro }]">
          <div v-if="m.papel === 'user'" class="balao">{{ m.conteudo }}</div>
          <div v-else class="balao">
            <div v-dompurify-html="markdown(m.conteudo)" class="md"></div>
            <div v-if="m.consultas?.length" class="consultas">
              <Icon class="mr-4px" icon="ep:search" />Consultou:
              <el-tooltip
                v-for="(c, j) in m.consultas"
                :key="j"
                :content="JSON.stringify(c.argumentos)"
                placement="top"
              >
                <el-tag class="ml-4px" disable-transitions size="small" type="info">{{
                  CONSULTAS[c.ferramenta] ?? c.ferramenta
                }}</el-tag>
              </el-tooltip>
            </div>
            <el-button
              v-if="detalheLogs(m.consultas)"
              class="mt-6px"
              plain
              size="small"
              type="primary"
              @click="verDetalhes(i)"
            >
              <Icon class="mr-4px" icon="ep:data-analysis" />Ver detalhes da pesquisa
            </el-button>
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
import * as Api from '@/api/assistente'
import { CONSULTAS } from '@/api/assistente'
import { EXEMPLOS, detalheLogs, markdown, quando, useConversa } from './conversa'

defineOptions({ name: 'AssistenteInterno' })
const message = useMessage()
const router = useRouter()

const sessoes = ref<Api.Sessao[]>([])
const texto = ref('')
const rolagem = ref<HTMLElement>()

const descer = () =>
  nextTick(() => rolagem.value && (rolagem.value.scrollTop = rolagem.value.scrollHeight))

const conversa = useConversa(descer)
const { sessaoId, mensagens, enviando, abrir } = conversa

const carregarSessoes = async () => {
  sessoes.value = await Api.getSessoes()
}

const nova = () => {
  conversa.nova()
  texto.value = ''
}

const enviar = async (pergunta?: string) => {
  const t = (pergunta ?? texto.value).trim()
  if (!t || enviando.value) return
  const antes = sessaoId.value
  texto.value = ''
  const r = await conversa.enviar(t)
  if (!r) {
    // a mensagem de erro já aparece pelo interceptador; devolve o texto para tentar de novo
    texto.value = t
  } else if (antes !== r.sessaoId) {
    carregarSessoes()
  }
}

const excluir = async (s: Api.Sessao) => {
  await message.delConfirm(`Apagar a conversa "${s.titulo}"?`)
  await Api.excluirSessao(s.id)
  if (sessaoId.value === s.id) nova()
  await carregarSessoes()
}

onMounted(carregarSessoes)

/** Abre a tela "Detalhes da pesquisa" com a consulta de logs da resposta (a pergunta vem da mensagem anterior) */
const verDetalhes = (i: number) => {
  const anterior = mensagens.value[i - 1]
  const destino = detalheLogs(mensagens.value[i].consultas, anterior?.papel === 'user' ? anterior.conteudo : undefined)
  if (destino) router.push(destino)
}
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
