<template>
  <div class="calendario">
    <ContentWrap>
      <div class="barra">
        <el-tree-select
          v-model="deptId"
          :data="depts"
          :props="defaultProps"
          check-strictly
          class="!w-260px"
          clearable
          filterable
          node-key="id"
          placeholder="Todas as unidades"
          @change="carregarAniversariantes"
        />
        <el-checkbox v-model="mostrarAniversarios">Aniversariantes</el-checkbox>
        <el-checkbox v-model="mostrarCompromissos">Meus compromissos</el-checkbox>
        <div class="flex-1"></div>
        <el-button @click="exportar"><Icon class="mr-4px" icon="ep:download" />Exportar aniversariantes do mês</el-button>
        <el-button type="primary" @click="novo(diaSelecionado)"><Icon class="mr-4px" icon="ep:plus" />Novo compromisso</el-button>
      </div>
      <p class="ajuda mt-8px">
        Aparece aqui quem informou a data de nascimento em Meu perfil e marcou como pública. Sua agenda só você vê: os
        outros veem apenas os compromissos para os quais você os convidar.
      </p>
    </ContentWrap>

    <el-row :gutter="12">
      <el-col :lg="17" :xs="24">
        <ContentWrap v-loading="carregando">
          <el-calendar v-model="dataAtual">
            <template #date-cell="{ data }">
              <div :class="['celula', { fora: data.type !== 'current-month', hoje: data.day === hojeIso }]">
                <div class="numero">{{ Number(data.day.slice(8)) }}</div>
                <template v-if="mostrarAniversarios">
                  <div
                    v-for="a in aniversariosDoDia(data.day)"
                    :key="`a${a.usuarioId}`"
                    :title="`${a.nome}${a.unidade ? ' · ' + a.unidade : ''}`"
                    class="item aniversario"
                  >
                    🎂 {{ primeiroNome(a.nome) }}
                  </div>
                </template>
                <template v-if="mostrarCompromissos">
                  <div
                    v-for="e in eventosDoDia(data.day)"
                    :key="`e${e.id}`"
                    :class="['item', 'evento', { convidado: !e.meu, pendente: e.minhaResposta === 'pendente' }]"
                    :style="{ background: e.cor || corPadrao }"
                    :title="e.meu ? e.titulo : `${e.titulo} (convite de ${e.dono})`"
                    @click.stop="abrir(e)"
                  >
                    <span v-if="!e.meu || e.participantes.length">👥 </span>
                    <span v-if="!e.diaInteiro">{{ hora(e.inicio) }} </span>{{ e.titulo }}
                  </div>
                </template>
              </div>
            </template>
          </el-calendar>
        </ContentWrap>
      </el-col>

      <el-col :lg="7" :xs="24">
        <ContentWrap :title="tituloDia">
          <template v-if="mostrarAniversarios && aniversariosDoDia(diaSelecionado).length">
            <div v-for="a in aniversariosDoDia(diaSelecionado)" :key="a.usuarioId" class="pessoa">
              <el-avatar :size="32" :src="a.avatar || undefined">{{ iniciais(a.nome) }}</el-avatar>
              <div>
                <div>🎂 {{ a.nome }}</div>
                <div class="ajuda">{{ a.unidade }}<span v-if="!a.publico"> · privado, só você vê</span></div>
              </div>
            </div>
          </template>
          <template v-if="mostrarCompromissos">
            <div v-for="e in eventosDoDia(diaSelecionado)" :key="e.id" class="compromisso" @click="abrir(e)">
              <span :style="{ background: e.cor || corPadrao }" class="bolinha"></span>
              <div class="flex-1">
                <div>{{ e.titulo }}</div>
                <div class="ajuda">{{ quando(e) }}<span v-if="e.local"> · {{ e.local }}</span></div>
                <div v-if="!e.meu" class="ajuda">Convite de {{ e.dono }}</div>
                <div v-else-if="e.participantes.length" class="ajuda">
                  {{ e.participantes.length }} {{ e.participantes.length === 1 ? 'convidado' : 'convidados' }}
                </div>
              </div>
              <el-tag v-if="!e.meu" :type="RESPOSTAS[e.minhaResposta || 'pendente'].tipo" size="small">
                {{ RESPOSTAS[e.minhaResposta || 'pendente'].rotulo }}
              </el-tag>
            </div>
          </template>
          <el-empty
            v-if="!aniversariosDoDia(diaSelecionado).length && !eventosDoDia(diaSelecionado).length"
            :image-size="60"
            description="Nada neste dia"
          />
          <el-button class="mt-8px w-full" plain type="primary" @click="novo(diaSelecionado)">
            Novo compromisso neste dia
          </el-button>
        </ContentWrap>

        <ContentWrap :title="`Aniversariantes de ${nomeMes}`">
          <el-empty v-if="!aniversariantesDoMes.length" :image-size="60" description="Ninguém neste mês" />
          <div
            v-for="a in aniversariantesDoMes"
            :key="a.usuarioId"
            :class="['pessoa', { 'e-hoje': a.dia.slice(5) === hojeIso.slice(5) }]"
          >
            <el-avatar :size="32" :src="a.avatar || undefined">{{ iniciais(a.nome) }}</el-avatar>
            <div class="flex-1">
              <div>{{ a.nome }}</div>
              <div class="ajuda">{{ a.unidade }}</div>
            </div>
            <el-tag :type="a.dia.slice(5) === hojeIso.slice(5) ? 'danger' : 'info'" size="small">
              {{ a.dia.slice(5) === hojeIso.slice(5) ? 'Hoje!' : diaMes(a.dia) }}
            </el-tag>
          </div>
        </ContentWrap>
      </el-col>
    </el-row>

    <el-dialog v-model="dialogo" :title="form.id ? 'Compromisso' : 'Novo compromisso'" width="520px">
      <el-form ref="formRef" :model="form" :rules="regras" label-position="top">
        <el-form-item label="Título" prop="titulo">
          <el-input v-model="form.titulo" maxlength="120" placeholder="Ex.: Reunião com a Saúde" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.diaInteiro">Dia inteiro</el-checkbox>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="Início" prop="inicio">
              <el-date-picker
                v-model="form.inicio"
                :format="form.diaInteiro ? 'DD/MM/YYYY' : 'DD/MM/YYYY HH:mm'"
                :type="form.diaInteiro ? 'date' : 'datetime'"
                class="!w-full"
                value-format="x"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Fim">
              <el-date-picker
                v-model="form.fim"
                :format="form.diaInteiro ? 'DD/MM/YYYY' : 'DD/MM/YYYY HH:mm'"
                :type="form.diaInteiro ? 'date' : 'datetime'"
                class="!w-full"
                clearable
                value-format="x"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Participantes">
          <el-select
            v-model="form.participantes"
            :remote-method="buscarPessoas"
            class="!w-full"
            filterable
            multiple
            placeholder="Procure pelo nome para convidar"
            remote
          >
            <el-option v-for="p in pessoasOpcoes" :key="p.id" :label="p.nome" :value="p.id">
              {{ p.nome }}<span v-if="p.unidade" class="ajuda"> · {{ p.unidade }}</span>
            </el-option>
          </el-select>
          <div class="ajuda">
            O compromisso aparece na agenda de quem você convidar, com aviso no sino. Só você pode alterar ou excluir.
          </div>
        </el-form-item>
        <div v-if="respostasDoEvento.length" class="respostas">
          <div v-for="p in respostasDoEvento" :key="p.id">
            <el-tag :type="RESPOSTAS[p.resposta].tipo" class="mr-6px" size="small">{{ RESPOSTAS[p.resposta].rotulo }}</el-tag>
            {{ p.nome }}
          </div>
        </div>
        <el-form-item label="Local">
          <el-input v-model="form.local" maxlength="200" />
        </el-form-item>
        <el-form-item label="Descrição">
          <el-input v-model="form.descricao" :rows="3" maxlength="2000" show-word-limit type="textarea" />
        </el-form-item>
        <el-form-item label="Cor">
          <el-color-picker v-model="form.cor" :predefine="cores" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="form.id" class="float-left" type="danger" plain @click="excluir">Excluir</el-button>
        <el-button @click="dialogo = false">Cancelar</el-button>
        <el-button :loading="salvando" type="primary" @click="salvar">Salvar</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dialogoConvite" title="Convite" width="480px">
      <template v-if="convite">
        <h3 class="mt-0">{{ convite.titulo }}</h3>
        <p><Icon class="mr-4px" icon="ep:clock" />{{ quando(convite) }}</p>
        <p v-if="convite.local"><Icon class="mr-4px" icon="ep:location" />{{ convite.local }}</p>
        <p><Icon class="mr-4px" icon="ep:user" />Convite de {{ convite.dono }}</p>
        <p v-if="convite.descricao" class="texto">{{ convite.descricao }}</p>
        <div class="respostas">
          <div v-for="p in convite.participantes" :key="p.id">
            <el-tag :type="RESPOSTAS[p.resposta].tipo" class="mr-6px" size="small">{{ RESPOSTAS[p.resposta].rotulo }}</el-tag>
            {{ p.nome }}
          </div>
        </div>
        <p class="ajuda">Sua resposta:</p>
        <el-radio-group :model-value="convite.minhaResposta ?? undefined" @change="(v) => responder(v as Api.Resposta)">
          <el-radio-button value="sim">Vou</el-radio-button>
          <el-radio-button value="talvez">Talvez</el-radio-button>
          <el-radio-button value="nao">Não vou</el-radio-button>
        </el-radio-group>
      </template>
      <template #footer>
        <el-button class="float-left" plain type="danger" @click="sairDoConvite">Sair do compromisso</el-button>
        <el-button @click="dialogoConvite = false">Fechar</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import * as Api from '@/api/calendario'
import * as DeptApi from '@/api/system/dept'
import { defaultProps, handleTree } from '@/utils/tree'
import download from '@/utils/download'

defineOptions({ name: 'Calendario' })

const message = useMessage()
const corPadrao = '#409eff'
const cores = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#9b59b6', '#16a085']

const iso = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const hojeIso = iso(new Date())

const dataAtual = ref(new Date())
const diaSelecionado = computed(() => iso(dataAtual.value))
const deptId = ref<number>()
const depts = ref<any[]>([])
const mostrarAniversarios = ref(true)
const mostrarCompromissos = ref(true)
const aniversariantes = ref<Api.Aniversariante[]>([])
const eventos = ref<Api.EventoVO[]>([])
const carregando = ref(false)
let anoCarregado = 0

const nomeMes = computed(() => dataAtual.value.toLocaleDateString('pt-BR', { month: 'long' }))
const tituloDia = computed(() =>
  dataAtual.value.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' })
)

// aniversário por "MM-DD": vale também para os dias do mês vizinho que aparecem na grade
const porDiaMes = computed(() => {
  const m = new Map<string, Api.Aniversariante[]>()
  for (const a of aniversariantes.value) {
    const k = a.dia.slice(5)
    m.set(k, [...(m.get(k) || []), a])
  }
  return m
})
const aniversariosDoDia = (dia: string) => porDiaMes.value.get(dia.slice(5)) || []
const aniversariantesDoMes = computed(() => {
  const mes = String(dataAtual.value.getMonth() + 1).padStart(2, '0')
  return aniversariantes.value.filter((a) => a.dia.slice(5, 7) === mes)
})

const eventosDoDia = (dia: string) => {
  const ini = new Date(`${dia}T00:00:00`).getTime()
  const fim = ini + 86_400_000
  return eventos.value.filter((e) => e.inicio < fim && (e.fim ?? e.inicio) >= ini)
}

const primeiroNome = (n: string) => n.split(' ')[0]
const iniciais = (n: string) =>
  n
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join('')
    .toUpperCase()
const diaMes = (d: string) => `${d.slice(8)}/${d.slice(5, 7)}`
const hora = (ms: number) => new Date(ms).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
const dataBr = (ms: number) => new Date(ms).toLocaleDateString('pt-BR')
const RESPOSTAS: Record<Api.Resposta, { rotulo: string; tipo: 'success' | 'danger' | 'warning' | 'info' }> = {
  sim: { rotulo: 'Vai', tipo: 'success' },
  nao: { rotulo: 'Não vai', tipo: 'danger' },
  talvez: { rotulo: 'Talvez', tipo: 'warning' },
  pendente: { rotulo: 'Sem resposta', tipo: 'info' }
}
const quando = (e: { inicio: number; fim?: number | null; diaInteiro: boolean }) => {
  if (e.diaInteiro) {
    return e.fim && dataBr(e.fim) !== dataBr(e.inicio) ? `${dataBr(e.inicio)} a ${dataBr(e.fim)}` : 'Dia inteiro'
  }
  const fim = e.fim ? (dataBr(e.fim) === dataBr(e.inicio) ? hora(e.fim) : `${dataBr(e.fim)} ${hora(e.fim)}`) : ''
  return fim ? `${hora(e.inicio)} – ${fim}` : hora(e.inicio)
}

const carregarAniversariantes = async () => {
  anoCarregado = dataAtual.value.getFullYear()
  aniversariantes.value = await Api.getAniversariantes({ ano: anoCarregado, deptId: deptId.value || undefined })
}

let mesCarregado = ''
const carregarEventos = async (forcar = false) => {
  const d = dataAtual.value
  const chave = `${d.getFullYear()}-${d.getMonth()}`
  if (!forcar && chave === mesCarregado) return
  mesCarregado = chave
  const de = new Date(d.getFullYear(), d.getMonth(), 1 - 7)
  const ate = new Date(d.getFullYear(), d.getMonth() + 1, 14)
  eventos.value = await Api.getEventos(iso(de), iso(ate))
}

const carregar = async () => {
  carregando.value = true
  try {
    await Promise.all([
      dataAtual.value.getFullYear() !== anoCarregado ? carregarAniversariantes() : null,
      carregarEventos()
    ])
  } finally {
    carregando.value = false
  }
}
watch(dataAtual, carregar)

const exportar = async () => {
  const d = dataAtual.value
  const blob = await Api.exportarAniversariantes({
    ano: d.getFullYear(),
    mes: d.getMonth() + 1,
    deptId: deptId.value || undefined
  })
  download.excel(blob as any, `aniversariantes-${String(d.getMonth() + 1).padStart(2, '0')}-${d.getFullYear()}.xlsx`)
}

// ---------- compromisso ----------
const dialogo = ref(false)
const salvando = ref(false)
const formRef = ref<FormInstance>()
const vazio = (): Api.Evento => ({
  titulo: '',
  inicio: 0,
  fim: null,
  diaInteiro: false,
  local: '',
  descricao: '',
  cor: null,
  participantes: []
})
const form = ref<Api.Evento>(vazio())
const respostasDoEvento = ref<Api.Participante[]>([])
const pessoasOpcoes = ref<Api.Pessoa[]>([])

const buscarPessoas = async (busca: string) => {
  const achadas = await Api.getPessoas(busca)
  // mantém as já escolhidas na lista, para o nome continuar aparecendo
  const escolhidas = pessoasOpcoes.value.filter((p) => form.value.participantes?.includes(p.id))
  pessoasOpcoes.value = [...escolhidas, ...achadas.filter((p) => !escolhidas.some((e) => e.id === p.id))]
}
const regras: FormRules = {
  titulo: [{ required: true, whitespace: true, message: 'Informe o título', trigger: 'blur' }],
  inicio: [{ required: true, message: 'Informe quando começa', trigger: 'change' }]
}

const novo = (dia: string) => {
  const agora = new Date()
  const inicio = new Date(`${dia}T00:00:00`)
  inicio.setHours(agora.getHours() + 1, 0, 0, 0)
  form.value = { ...vazio(), inicio: inicio.getTime() }
  respostasDoEvento.value = []
  dialogo.value = true
  buscarPessoas('')
  nextTick(() => formRef.value?.clearValidate())
}

const editar = (e: Api.EventoVO) => {
  form.value = {
    id: e.id,
    titulo: e.titulo,
    descricao: e.descricao,
    local: e.local,
    inicio: e.inicio,
    fim: e.fim,
    diaInteiro: e.diaInteiro,
    cor: e.cor,
    participantes: e.participantes.map((p) => p.id)
  }
  respostasDoEvento.value = e.participantes
  pessoasOpcoes.value = e.participantes.map((p) => ({ id: p.id, nome: p.nome, unidade: null }))
  dialogo.value = true
  nextTick(() => formRef.value?.clearValidate())
}

// ---------- convite (compromisso de outra pessoa) ----------
const dialogoConvite = ref(false)
const convite = ref<Api.EventoVO>()

const abrir = (e: Api.EventoVO) => {
  if (e.meu) {
    editar(e)
    return
  }
  convite.value = e
  dialogoConvite.value = true
}

const responder = async (r: Api.Resposta) => {
  if (!convite.value) return
  await Api.responder(convite.value.id, r)
  message.success('Resposta enviada')
  dialogoConvite.value = false
  await carregarEventos(true)
}

const sairDoConvite = async () => {
  if (!convite.value) return
  try {
    await message.confirm(`Sair de "${convite.value.titulo}"? Ele some da sua agenda e ${convite.value.dono} fica sabendo.`)
  } catch {
    return
  }
  await Api.sair(convite.value.id)
  dialogoConvite.value = false
  message.success('Você saiu do compromisso')
  await carregarEventos(true)
}

/** Aviso do sino: /calendario?evento=ID abre o compromisso no dia dele. */
const route = useRoute()
const abrirDoLink = async () => {
  const id = Number(route.query.evento)
  if (!id) return
  try {
    const e = await Api.getEvento(id)
    dataAtual.value = new Date(e.inicio)
    await carregar()
    abrir(e)
  } catch {
    // compromisso cancelado ou a pessoa saiu: o erro já aparece na tela
  }
}
watch(() => route.query.evento, abrirDoLink)

const salvar = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  const f = form.value
  const dados: Api.Evento = { ...f, inicio: Number(f.inicio), fim: f.fim ? Number(f.fim) : null }
  if (dados.fim && dados.fim < dados.inicio) {
    message.warning('O fim não pode ser antes do início')
    return
  }
  salvando.value = true
  try {
    if (f.id) await Api.alterarEvento(f.id, dados)
    else await Api.criarEvento(dados)
    dialogo.value = false
    message.success('Compromisso salvo')
    await carregarEventos(true)
  } finally {
    salvando.value = false
  }
}

const excluir = async () => {
  try {
    await message.delConfirm(`Excluir o compromisso "${form.value.titulo}"?`)
  } catch {
    return
  }
  await Api.excluirEvento(form.value.id!)
  dialogo.value = false
  message.success('Compromisso excluído')
  await carregarEventos(true)
}

onMounted(async () => {
  DeptApi.getSimpleDeptList().then((l) => (depts.value = handleTree(l)))
  await carregar()
  await abrirDoLink()
})
</script>

<style lang="scss" scoped>
.barra {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.ajuda {
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

:deep(.el-calendar-table .el-calendar-day) {
  height: 104px;
  padding: 4px;
  overflow: hidden;
}

.celula {
  height: 100%;
  overflow: hidden;

  &.fora {
    opacity: 0.45;
  }

  &.hoje .numero {
    display: inline-block;
    min-width: 22px;
    color: #fff;
    text-align: center;
    background: var(--el-color-primary);
    border-radius: 11px;
  }
}

.numero {
  margin-bottom: 2px;
  font-size: 13px;
}

.item {
  overflow: hidden;
  padding: 0 4px;
  margin-bottom: 2px;
  font-size: 11px;
  line-height: 17px;
  white-space: nowrap;
  text-overflow: ellipsis;
  border-radius: 3px;
}

.aniversario {
  color: var(--el-color-danger-dark-2);
  background: var(--el-color-danger-light-9);
}

.evento {
  color: #fff;
  cursor: pointer;

  // convite de outra pessoa: borda tracejada; sem resposta ainda, mais apagado
  &.convidado {
    outline: 1px dashed rgb(255 255 255 / 70%);
    outline-offset: -2px;
  }

  &.pendente {
    opacity: 0.6;
  }
}

.respostas {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin: 4px 0 14px;
  font-size: 13px;
}

.texto {
  white-space: pre-wrap;
}

.pessoa,
.compromisso {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 4px;
  font-size: 13px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.compromisso {
  cursor: pointer;

  &:hover {
    background: var(--el-fill-color-light);
  }
}

.e-hoje {
  background: var(--el-color-danger-light-9);
  border-radius: 4px;
}

.bolinha {
  flex: none;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

@media (width <= 768px) {
  :deep(.el-calendar-table .el-calendar-day) {
    height: 64px;
  }

  .item {
    font-size: 10px;
  }
}
</style>
