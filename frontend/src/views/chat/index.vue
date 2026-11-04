<template>
  <div class="chat">
    <aside class="lista">
      <div class="p-10px">
        <el-input v-model="filtro" clearable placeholder="Procurar conversa">
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
        <div class="mt-8px flex gap-6px">
          <el-button class="flex-1" plain size="small" type="primary" @click="abrirNova('direta')">Nova conversa</el-button>
          <el-button class="flex-1" plain size="small" @click="abrirNova('grupo')">Novo grupo</el-button>
          <el-button plain size="small" title="Buscar nas mensagens" @click="busca.visivel = true">
            <Icon icon="ep:search" />
          </el-button>
        </div>
        <el-link
          v-if="podeAtivarAvisos"
          class="mt-6px !text-12px"
          type="primary"
          @click="ativarAvisos"
          >Ativar avisos no navegador</el-link
        >
      </div>
      <el-scrollbar class="flex-1">
        <el-empty v-if="!conversasVisiveis.length && !carregando" description="Nenhuma conversa ainda" />
        <div
          v-for="c in conversasVisiveis"
          :key="c.id"
          class="item"
          :class="{ ativa: c.id === atual?.id }"
          @click="abrir(c.id)"
        >
          <el-badge :hidden="c.tipo !== 'direta' || !c.online" is-dot type="success">
            <el-avatar :size="36" :src="c.avatar">
              <Icon v-if="c.tipo === 'grupo'" icon="ep:user-filled" />
              <template v-else>{{ c.nome?.charAt(0) }}</template>
            </el-avatar>
          </el-badge>
          <div class="min-w-0 flex-1">
            <div class="flex justify-between gap-6px">
              <span class="nome">{{ c.nome }}</span>
              <span class="quando">{{ quando(c.ultimaMensagemEm) }}</span>
            </div>
            <div class="flex justify-between gap-6px">
              <span class="ultimo">{{ c.ultimoTexto }}</span>
              <el-badge v-if="c.naoLidas" :max="99" :value="c.naoLidas" />
            </div>
          </div>
        </div>
      </el-scrollbar>
    </aside>

    <main class="painel">
      <el-empty v-if="!atual" description="Escolha uma conversa ou comece uma nova" />
      <template v-else>
        <header class="topo">
          <div class="min-w-0">
            <div class="font-bold">{{ atual.nome }}</div>
            <div class="text-12px text-[var(--el-text-color-secondary)]">
              <template v-if="atual.tipo === 'direta'">
                {{ atual.online ? 'Online' : 'Fora do chat' }}
                <el-tag v-if="atual.ausente" class="ml-4px" size="small" type="warning">
                  Ausente{{ atual.ausenteAte ? ` até ${dataBr(atual.ausenteAte)}` : '' }}
                </el-tag>
              </template>
              <template v-else>{{ atual.membros?.length }} pessoas</template>
            </div>
          </div>
          <el-button v-if="atual.tipo === 'grupo'" link @click="grupo.visivel = true">
            <Icon class="mr-4px" icon="ep:setting" />Grupo
          </el-button>
        </header>

        <div v-if="desconectado" class="aviso">Sem conexão em tempo real. Tentando reconectar…</div>

        <div ref="rolagem" class="mensagens" @scroll.passive="aoRolar">
          <div v-if="temMais" class="text-center">
            <el-button link :loading="carregandoMais" @click="carregarAntigas">Mensagens anteriores</el-button>
          </div>
          <ChatMensagem
            v-for="m in mensagens"
            :key="m.clienteId || m.id"
            :admin="atual.papel === 'admin'"
            :grupo="atual.tipo === 'grupo'"
            :m="m"
            :minha="m.remetenteId === meuId"
            @apagar="apagar"
            @editar="iniciarEdicao"
            @reenviar="reenviar"
            @responder="(x: any) => (respondendo = x)"
          />
        </div>

        <footer class="compor">
          <div v-if="respondendo" class="contexto">
            <span>Respondendo a <b>{{ respondendo.remetente }}</b>: {{ (respondendo.conteudo || 'Anexo').slice(0, 80) }}</span>
            <el-button link @click="respondendo = null"><Icon icon="ep:close" /></el-button>
          </div>
          <div v-if="editando" class="contexto">
            <span>Editando a mensagem</span>
            <el-button link @click="cancelarEdicao"><Icon icon="ep:close" /></el-button>
          </div>
          <div v-if="anexos.length" class="contexto flex-wrap">
            <el-tag v-for="a in anexos" :key="a.id" closable @close="anexos.splice(anexos.indexOf(a), 1)">{{ a.nome }}</el-tag>
          </div>
          <div v-if="sugestoes.length" class="sugestoes">
            <div v-for="s in sugestoes" :key="s.id" class="sugestao" @mousedown.prevent="escolherMencao(s)">{{ s.nome }}</div>
          </div>
          <div class="flex items-end gap-6px">
            <el-upload :auto-upload="false" :disabled="!!editando" :on-change="aoEscolherArquivo" :show-file-list="false" multiple>
              <el-button :disabled="!!editando" :loading="subindo" title="Anexar"><Icon icon="ep:paperclip" /></el-button>
            </el-upload>
            <ChatEmojis @escolher="inserirEmoji" />
            <el-button
              :type="ausencia.dados.ativa ? 'warning' : undefined"
              :plain="ausencia.dados.ativa"
              title="Resposta automática (férias ou ausência)"
              @click="abrirAusencia"
            >
              <Icon icon="ep:timer" />
            </el-button>
            <el-input
              ref="campo"
              v-model="texto"
              :autosize="{ minRows: 1, maxRows: 6 }"
              maxlength="10000"
              placeholder="Escreva uma mensagem. Enter envia, Shift+Enter quebra a linha, @ menciona."
              type="textarea"
              @input="aoDigitar"
              @keydown.enter="aoEnter"
            />
            <el-button :disabled="!podeEnviar" type="primary" @click="enviar">
              <Icon icon="ep:promotion" />
            </el-button>
          </div>
        </footer>
      </template>
    </main>

    <Dialog v-model="nova.visivel" :title="nova.tipo === 'grupo' ? 'Novo grupo' : 'Nova conversa'" width="480px">
      <el-input v-if="nova.tipo === 'grupo'" v-model="nova.nome" class="mb-10px" maxlength="100" placeholder="Nome do grupo" />
      <el-input v-model="nova.busca" clearable placeholder="Procurar pessoa pelo nome ou login" @input="procurarPessoas">
        <template #prefix><Icon icon="ep:search" /></template>
      </el-input>
      <div v-if="nova.tipo === 'grupo' && nova.escolhidos.length" class="my-8px flex flex-wrap gap-4px">
        <el-tag v-for="p in nova.escolhidos" :key="p.id" closable @close="alternarPessoa(p)">{{ p.nome }}</el-tag>
      </div>
      <el-scrollbar class="mt-8px" max-height="300px">
        <div v-for="p in nova.pessoas" :key="p.id" class="pessoa" @click="escolherPessoa(p)">
          <el-badge :hidden="!p.online" is-dot type="success"><el-avatar :size="30" :src="p.avatar">{{ p.nome.charAt(0) }}</el-avatar></el-badge>
          <div class="min-w-0 flex-1">
            <div>{{ p.nome }}</div>
            <div class="text-12px text-[var(--el-text-color-secondary)]">{{ p.unidade }}</div>
          </div>
          <Icon v-if="nova.escolhidos.some((x) => x.id === p.id)" icon="ep:check" />
        </div>
      </el-scrollbar>
      <template v-if="nova.tipo === 'grupo'" #footer>
        <el-button :disabled="!nova.nome.trim() || !nova.escolhidos.length" type="primary" @click="criarGrupo">Criar grupo</el-button>
      </template>
    </Dialog>

    <Dialog v-model="ausencia.visivel" title="Resposta automática" width="480px">
      <el-form label-position="top">
        <el-form-item label="Responder automaticamente">
          <el-switch v-model="ausencia.dados.ativa" />
        </el-form-item>
        <el-form-item label="Mensagem">
          <el-input
            v-model="ausencia.dados.mensagem"
            maxlength="500"
            placeholder="Ex.: Estou de férias até 20/11. Para assuntos urgentes, fale com a equipe do Service Desk."
            show-word-limit
            type="textarea"
          />
        </el-form-item>
        <div class="flex gap-10px">
          <el-form-item class="flex-1" label="De (opcional)">
            <el-date-picker v-model="ausencia.dados.inicio" class="!w-full" format="DD/MM/YYYY" type="date" value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item class="flex-1" label="Até (opcional)">
            <el-date-picker v-model="ausencia.dados.fim" class="!w-full" format="DD/MM/YYYY" type="date" value-format="YYYY-MM-DD" />
          </el-form-item>
        </div>
        <div class="text-12px text-[var(--el-text-color-secondary)]">
          Vale em conversas diretas, com no máximo uma resposta a cada 24 horas por conversa. Com data final, desliga sozinha.
        </div>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="salvarAusencia">Salvar</el-button>
        <el-button @click="ausencia.visivel = false">Cancelar</el-button>
      </template>
    </Dialog>

    <Dialog v-model="busca.visivel" title="Buscar nas mensagens" width="600px">
      <el-input v-model="busca.texto" clearable placeholder="Digite ao menos 2 letras e tecle Enter" @keyup.enter="buscarMensagens">
        <template #prefix><Icon icon="ep:search" /></template>
      </el-input>
      <el-empty v-if="busca.feita && !busca.achados.length" description="Nada encontrado" />
      <el-scrollbar class="mt-8px" max-height="400px">
        <div v-for="a in busca.achados" :key="a.mensagem.id" class="pessoa" @click="irPara(a)">
          <div class="min-w-0 flex-1">
            <div class="text-12px text-[var(--el-text-color-secondary)]">
              {{ a.conversa }} · {{ a.mensagem.remetente }} · {{ quando(a.mensagem.criadaEm) }}
            </div>
            <div class="truncate">{{ a.mensagem.conteudo }}</div>
          </div>
        </div>
      </el-scrollbar>
    </Dialog>

    <Dialog v-model="grupo.visivel" :title="atual?.nome" width="480px">
      <template v-if="atual?.papel === 'admin'">
        <div class="mb-10px flex gap-6px">
          <el-input v-model="grupo.nome" maxlength="100" placeholder="Novo nome do grupo" />
          <el-button :disabled="!grupo.nome.trim()" @click="renomear">Renomear</el-button>
        </div>
        <el-select
          v-model="grupo.novo"
          class="mb-10px w-full"
          :filter-method="procurarParaGrupo"
          filterable
          placeholder="Adicionar pessoa"
          @change="adicionar"
        >
          <el-option v-for="p in grupo.pessoas" :key="p.id" :label="p.nome" :value="p.id" />
        </el-select>
        <div class="mb-8px text-12px text-[var(--el-text-color-secondary)]">
          Quem entra agora só vê as mensagens a partir de agora.
        </div>
      </template>
      <el-scrollbar max-height="300px">
        <div v-for="p in atual?.membros" :key="p.id" class="pessoa !cursor-default">
          <el-badge :hidden="!p.online" is-dot type="success"><el-avatar :size="28" :src="p.avatar">{{ p.nome.charAt(0) }}</el-avatar></el-badge>
          <div class="flex-1">{{ p.nome }} <el-tag v-if="p.papel === 'admin'" size="small">admin</el-tag></div>
          <el-button v-if="atual?.papel === 'admin' && p.id !== meuId" link type="danger" @click="remover(p)">Remover</el-button>
        </div>
      </el-scrollbar>
      <template #footer>
        <el-button type="danger" plain @click="sair">Sair do grupo</el-button>
      </template>
    </Dialog>
  </div>
</template>

<script lang="ts" setup>
import * as Api from '@/api/chat'
import { useUserStore } from '@/store/modules/user'
import { useMonitorSocket } from '@/hooks/web/useMonitorSocket'
import { atualizarNaoLidas, iniciarChat, useChat } from './useChat'
import ChatMensagem from './ChatMensagem.vue'
import ChatEmojis from './ChatEmojis.vue'

/** A tela confia no número sequencial: a mensagem nova entra pelo WebSocket e, se faltar alguma, ela é buscada. */
defineOptions({ name: 'Chat' })
const message = useMessage()
const meuId = useUserStore().getUser.id
const { conversaAberta, ouvir, aoReconectar } = useChat()
const { status } = useMonitorSocket()

const carregando = ref(true)
const conversas = ref<Api.Conversa[]>([])
const filtro = ref('')
const atual = ref<Api.Conversa | null>(null)
const mensagens = ref<any[]>([])
const temMais = ref(false)
const carregandoMais = ref(false)
const rolagem = ref<HTMLElement>()
const campo = ref()
const texto = ref('')
const anexos = ref<Api.Anexo[]>([])
const subindo = ref(false)
const respondendo = ref<any>(null)
const editando = ref<any>(null)
const mencoes = ref<Api.Membro[]>([])
const sugestoes = ref<Api.Membro[]>([])
const desconectado = computed(() => status.value !== 'OPEN')
const permissao = ref(typeof Notification === 'undefined' ? 'granted' : Notification.permission)
const podeAtivarAvisos = computed(() => permissao.value === 'default')

const conversasVisiveis = computed(() => {
  const f = filtro.value.trim().toLowerCase()
  return f ? conversas.value.filter((c) => c.nome?.toLowerCase().includes(f)) : conversas.value
})
const podeEnviar = computed(() => !!texto.value.trim() || anexos.value.length > 0)

const quando = (t?: number) => {
  if (!t) return ''
  const d = new Date(t)
  return d.toDateString() === new Date().toDateString()
    ? d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
    : d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
}
const ativarAvisos = async () => {
  permissao.value = await Notification.requestPermission()
}

const noFim = () => {
  const r = rolagem.value
  return !r || r.scrollHeight - r.scrollTop - r.clientHeight < 80
}
const rolarFim = () => nextTick(() => rolagem.value && (rolagem.value.scrollTop = rolagem.value.scrollHeight))
const ultimaSeq = () => mensagens.value.reduce((s, m) => (m.seq && m.seq > s ? m.seq : s), 0)

const carregarConversas = async () => {
  conversas.value = await Api.getConversas()
  carregando.value = false
}

const marcarLida = () => {
  const c = atual.value
  const seq = ultimaSeq()
  if (!c || !seq || document.visibilityState !== 'visible' || !noFim()) return
  const l = conversas.value.find((x) => x.id === c.id)
  if (l) l.naoLidas = 0
  Api.marcarLida(c.id, seq).then(atualizarNaoLidas).catch(() => {})
}

const abrir = async (id: number, irParaSeq?: number) => {
  atual.value = await Api.getConversa(id)
  conversaAberta.value = id
  respondendo.value = null
  cancelarEdicao()
  anexos.value = []
  const lista = await Api.getMensagens({ conversaId: id, limite: 50 })
  mensagens.value = lista
  temMais.value = lista.length === 50
  await rolarFim()
  if (irParaSeq) await irParaMensagem(irParaSeq)
  marcarLida()
}

const irParaMensagem = async (seq: number) => {
  // busca para trás até a mensagem estar na tela, sem limite por ser conversa que a pessoa já lê
  while (temMais.value && !mensagens.value.some((m) => m.seq === seq)) await carregarAntigas()
  await nextTick()
  rolagem.value?.querySelector(`[data-seq="${seq}"]`)?.scrollIntoView({ block: 'center' })
}

const carregarAntigas = async () => {
  if (!atual.value || carregandoMais.value) return
  const primeira = mensagens.value.find((m) => m.seq)
  if (!primeira) return
  carregandoMais.value = true
  const r = rolagem.value
  const altura = r?.scrollHeight || 0
  try {
    const l = await Api.getMensagens({ conversaId: atual.value.id, antesDe: primeira.seq, limite: 50 })
    mensagens.value = [...l, ...mensagens.value]
    temMais.value = l.length === 50
    await nextTick()
    if (r) r.scrollTop += r.scrollHeight - altura
  } finally {
    carregandoMais.value = false
  }
}
const aoRolar = () => {
  if (rolagem.value && rolagem.value.scrollTop < 60 && temMais.value) carregarAntigas()
  if (noFim()) marcarLida()
}

/** Une sem duplicar: a mesma mensagem pode chegar pelo envio e pelo WebSocket. */
const receber = (m: any) => {
  const lista = mensagens.value
  const i = lista.findIndex((x) => x.id === m.id || (m.clienteId && x.clienteId === m.clienteId))
  if (i >= 0) {
    lista[i] = { ...m }
    return
  }
  // chegou fora de ordem ou depois de um buraco: busca o que faltou
  const ult = ultimaSeq()
  if (ult && m.seq > ult + 1) completar()
  const pendentes = lista.findIndex((x) => !x.seq)
  if (pendentes >= 0) lista.splice(pendentes, 0, m)
  else lista.push(m)
}

const completar = async () => {
  const c = atual.value
  if (!c) return
  for (let i = 0; i < 20; i++) {
    const l = await Api.getMensagens({ conversaId: c.id, depoisDe: ultimaSeq(), limite: 100 })
    if (atual.value?.id !== c.id) return
    l.forEach(receber)
    if (l.length < 100) break
  }
  marcarLida()
}

const gerarId = () => (crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`).slice(0, 40)

const enviar = async () => {
  if (!podeEnviar.value || !atual.value) return
  if (editando.value) return salvarEdicao()
  const nomes = mencoes.value.filter((p) => texto.value.includes(`@${p.nome}`))
  const provisoria = {
    clienteId: gerarId(),
    conversaId: atual.value.id,
    remetenteId: meuId,
    tipo: 'texto',
    conteudo: texto.value.trim(),
    apagada: false,
    editada: false,
    mencoes: nomes.map((p) => ({ id: p.id, nome: p.nome })),
    anexos: [...anexos.value],
    resposta: respondendo.value
      ? { id: respondendo.value.id, remetente: respondendo.value.remetente, trecho: (respondendo.value.conteudo || 'Anexo').slice(0, 120) }
      : undefined,
    criadaEm: Date.now(),
    enviando: true,
    falhou: false
  }
  mensagens.value.push(provisoria)
  texto.value = ''
  anexos.value = []
  respondendo.value = null
  mencoes.value = []
  sugestoes.value = []
  rolarFim()
  await transmitir(provisoria)
}

const transmitir = async (p: any) => {
  try {
    const r = await Api.enviar({
      conversaId: p.conversaId,
      clienteId: p.clienteId,
      conteudo: p.conteudo,
      respostaId: p.resposta?.id,
      mencoes: p.mencoes.map((x: any) => x.id),
      anexoIds: p.anexos.map((a: any) => a.id)
    })
    receber(r)
    carregarConversas()
  } catch {
    // o mesmo número da tentativa garante que reenviar não duplica, mesmo se o servidor chegou a gravar
    const i = mensagens.value.findIndex((x) => x.clienteId === p.clienteId)
    if (i >= 0) mensagens.value[i] = { ...mensagens.value[i], enviando: false, falhou: true }
  }
}
const reenviar = (m: any) => {
  const i = mensagens.value.findIndex((x) => x.clienteId === m.clienteId)
  if (i >= 0) mensagens.value[i] = { ...m, enviando: true, falhou: false }
  transmitir(m)
}
const aoEnter = (e: KeyboardEvent) => {
  if (e.shiftKey) return
  e.preventDefault()
  enviar()
}

const iniciarEdicao = (m: any) => {
  editando.value = m
  texto.value = m.conteudo
  mencoes.value = (m.mencoes || []).map((p: any) => ({ ...p, papel: 'membro' }))
  respondendo.value = null
  campo.value?.focus()
}
const cancelarEdicao = () => {
  if (editando.value) texto.value = ''
  editando.value = null
}
const salvarEdicao = async () => {
  const nomes = mencoes.value.filter((p) => texto.value.includes(`@${p.nome}`))
  await Api.editar({ mensagemId: editando.value.id, conteudo: texto.value, mencoes: nomes.map((p) => p.id) })
  editando.value = null
  texto.value = ''
  mencoes.value = []
}
const apagar = async (m: any) => {
  await message.confirm('Apagar esta mensagem para todos? Quem administra o sistema ainda pode consultá-la em uma apuração.')
  await Api.apagar(m.id)
}

const aoEscolherArquivo = async (f: any) => {
  if (!atual.value) return
  subindo.value = true
  try {
    anexos.value.push(await Api.anexar(atual.value.id, f.raw))
  } finally {
    subindo.value = false
  }
}

const dataBr = (d: string) => d.split('-').reverse().join('/')

/** Entra onde o cursor está, não no fim do texto. */
const inserirEmoji = (e: string) => {
  const area: HTMLTextAreaElement | undefined = campo.value?.textarea
  const ini = area?.selectionStart ?? texto.value.length
  const fim = area?.selectionEnd ?? texto.value.length
  texto.value = texto.value.slice(0, ini) + e + texto.value.slice(fim)
  nextTick(() => {
    area?.focus()
    area?.setSelectionRange(ini + e.length, ini + e.length)
  })
}

const ausencia = reactive<{ visivel: boolean; dados: Api.Ausencia }>({ visivel: false, dados: { ativa: false } })
const abrirAusencia = async () => {
  ausencia.dados = await Api.getAusencia()
  ausencia.visivel = true
}
const salvarAusencia = async () => {
  await Api.salvarAusencia(ausencia.dados)
  ausencia.visivel = false
  message.success(ausencia.dados.ativa ? 'Resposta automática ligada' : 'Resposta automática desligada')
}

const aoDigitar = () => {
  const m = texto.value.match(/@([^\s@]*)$/)
  if (!m || atual.value?.tipo !== 'grupo') {
    sugestoes.value = []
    return
  }
  const t = m[1].toLowerCase()
  sugestoes.value = (atual.value.membros || []).filter((p) => p.id !== meuId && p.nome.toLowerCase().includes(t)).slice(0, 6)
}
const escolherMencao = (p: Api.Membro) => {
  texto.value = texto.value.replace(/@([^\s@]*)$/, `@${p.nome} `)
  if (!mencoes.value.some((x) => x.id === p.id)) mencoes.value.push(p)
  sugestoes.value = []
  campo.value?.focus()
}

const nova = reactive<{ visivel: boolean; tipo: string; nome: string; busca: string; pessoas: Api.Pessoa[]; escolhidos: Api.Pessoa[] }>({
  visivel: false,
  tipo: 'direta',
  nome: '',
  busca: '',
  pessoas: [],
  escolhidos: []
})
const abrirNova = async (tipo: string) => {
  Object.assign(nova, { visivel: true, tipo, nome: '', busca: '', escolhidos: [] })
  nova.pessoas = await Api.getPessoas('')
}
let atraso: any
const procurarPessoas = () => {
  clearTimeout(atraso)
  atraso = setTimeout(async () => (nova.pessoas = await Api.getPessoas(nova.busca)), 250)
}
const alternarPessoa = (p: Api.Pessoa) => {
  const i = nova.escolhidos.findIndex((x) => x.id === p.id)
  if (i >= 0) nova.escolhidos.splice(i, 1)
  else nova.escolhidos.push(p)
}
const escolherPessoa = async (p: Api.Pessoa) => {
  if (nova.tipo === 'grupo') return alternarPessoa(p)
  const id = await Api.abrirDireta(p.id)
  nova.visivel = false
  await carregarConversas()
  await abrir(id)
}
const criarGrupo = async () => {
  const id = await Api.criarGrupo({ nome: nova.nome, membros: nova.escolhidos.map((p) => p.id) })
  nova.visivel = false
  await carregarConversas()
  await abrir(id)
}

const busca = reactive<{ visivel: boolean; texto: string; feita: boolean; achados: Api.Achado[] }>({
  visivel: false,
  texto: '',
  feita: false,
  achados: []
})
const buscarMensagens = async () => {
  busca.achados = await Api.buscar(busca.texto)
  busca.feita = true
}
const irPara = async (a: Api.Achado) => {
  busca.visivel = false
  await abrir(a.mensagem.conversaId, a.mensagem.seq)
}

const grupo = reactive<{ visivel: boolean; nome: string; novo?: number; pessoas: Api.Pessoa[] }>({ visivel: false, nome: '', pessoas: [] })
const recarregarAtual = async () => {
  if (atual.value) atual.value = await Api.getConversa(atual.value.id)
}
const renomear = async () => {
  await Api.renomearGrupo({ conversaId: atual.value!.id, nome: grupo.nome })
  grupo.nome = ''
}
const procurarParaGrupo = async (t: string) => {
  grupo.pessoas = await Api.getPessoas(t)
}
const adicionar = async (id: number) => {
  await Api.adicionarMembros({ conversaId: atual.value!.id, userIds: [id] })
  grupo.novo = undefined
}
const remover = async (p: Api.Membro) => {
  await message.confirm(`Remover ${p.nome} do grupo?`)
  await Api.removerMembro(atual.value!.id, p.id)
}
const sair = async () => {
  await message.confirm('Sair deste grupo? Você deixa de ver as mensagens.')
  await Api.sairDoGrupo(atual.value!.id)
  grupo.visivel = false
  atual.value = null
  conversaAberta.value = null
  mensagens.value = []
  await carregarConversas()
}

const desligar = [
  ouvir(async (tipo, c) => {
    if (tipo === 'chat-mensagem') {
      if (atual.value?.id === c.conversaId) {
        const fim = noFim() || c.remetenteId === meuId
        receber(c)
        if (fim) await rolarFim()
        marcarLida()
      }
      carregarConversas()
    } else if (tipo === 'chat-atualizada') {
      if (atual.value?.id === c.conversaId) {
        const i = mensagens.value.findIndex((m) => m.id === c.id)
        if (i >= 0) mensagens.value[i] = c
      }
      carregarConversas()
    } else if (tipo === 'chat-conversa') {
      await carregarConversas()
      if (atual.value?.id === c.conversaId) {
        if (conversas.value.some((x) => x.id === c.conversaId)) await recarregarAtual()
        else {
          // a pessoa foi removida do grupo
          atual.value = null
          conversaAberta.value = null
          mensagens.value = []
        }
      }
    } else if (tipo === 'chat-lida') {
      carregarConversas()
    }
  }),
  aoReconectar(() => {
    carregarConversas()
    completar()
  })
]

const aoVoltarATela = () => {
  if (document.visibilityState === 'visible') marcarLida()
}
onMounted(async () => {
  iniciarChat()
  Api.getAusencia().then((a) => (ausencia.dados = a)).catch(() => {})
  document.addEventListener('visibilitychange', aoVoltarATela)
  await carregarConversas()
})
onActivated(() => {
  if (atual.value) conversaAberta.value = atual.value.id
})
onDeactivated(() => (conversaAberta.value = null))
onBeforeUnmount(() => {
  conversaAberta.value = null
  document.removeEventListener('visibilitychange', aoVoltarATela)
  desligar.forEach((off) => off())
})
</script>

<style scoped>
.chat {
  display: flex;
  height: calc(100vh - 110px);
  min-height: 420px;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.lista {
  display: flex;
  width: 300px;
  flex-direction: column;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-lighter);
}

.item {
  display: flex;
  padding: 8px 10px;
  cursor: pointer;
  align-items: center;
  gap: 10px;
}

.item:hover,
.pessoa:hover {
  background: var(--el-fill-color-light);
}

.item.ativa {
  background: var(--el-color-primary-light-9);
}

.nome {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ultimo {
  overflow: hidden;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quando {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.painel {
  display: flex;
  min-width: 0;
  justify-content: center;
  flex: 1;
  flex-direction: column;
}

.topo {
  display: flex;
  padding: 10px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  justify-content: space-between;
  align-items: center;
}

.aviso {
  padding: 4px 14px;
  font-size: 12px;
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}

.mensagens {
  padding: 10px 14px;
  overflow-y: auto;
  flex: 1;
}

.compor {
  position: relative;
  padding: 10px 14px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.contexto {
  display: flex;
  padding: 4px 8px;
  margin-bottom: 6px;
  font-size: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
}

.sugestoes {
  position: absolute;
  bottom: 100%;
  left: 60px;
  z-index: 5;
  min-width: 220px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
}

.sugestao {
  padding: 6px 10px;
  cursor: pointer;
}

.sugestao:hover {
  background: var(--el-fill-color);
}

.pessoa {
  display: flex;
  padding: 6px 8px;
  cursor: pointer;
  border-radius: 4px;
  align-items: center;
  gap: 8px;
}

@media (width <= 768px) {
  .lista {
    width: 40%;
    min-width: 150px;
  }
}
</style>
