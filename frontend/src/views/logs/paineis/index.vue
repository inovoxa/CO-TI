<!-- Painéis de logs no estilo do Graylog: abas, filtro para todos os quadros, contadores, rankings, mensagens. -->
<template>
  <div class="paineis">
    <!-- filtro geral -->
    <div class="filtro">
      <el-tooltip content="Aplicar o filtro" placement="bottom">
        <el-button class="aplicar" type="success" @click="aplicar"><Icon icon="ep:filter" :size="18" /></el-button>
      </el-tooltip>
      <el-input
        v-model="filtroDigitado"
        class="consulta"
        clearable
        placeholder="Filtro para todos os quadros (ex.: usuario:joao.silva  ou  srcip:172.21.1.220)"
        @keyup.enter="aplicar"
        @clear="aplicar"
      />
      <el-select v-model="periodoGeral" class="!w-200px" placeholder="Período de cada quadro" clearable>
        <el-option label="Período de cada quadro" value="" />
        <el-option v-for="p in Api.PERIODOS" :key="p.valor" :label="p.rotulo" :value="p.valor" />
      </el-select>
      <el-select v-model="intervalo" class="!w-150px">
        <el-option :value="0" label="Sem atualizar" />
        <el-option :value="30" label="A cada 30 s" />
        <el-option :value="60" label="A cada 1 min" />
        <el-option :value="300" label="A cada 5 min" />
      </el-select>
      <el-tooltip content="Atualizar agora" placement="bottom">
        <el-button @click="atualizar"><Icon icon="ep:refresh" /></el-button>
      </el-tooltip>
      <el-button v-if="podeEditar" :type="editando ? 'primary' : 'default'" @click="editando = !editando">
        <Icon class="mr-4px" icon="ep:edit" />{{ editando ? 'Concluir edição' : 'Editar' }}
      </el-button>
    </div>

    <!-- abas -->
    <div class="abas">
      <div
        v-for="p in paineis"
        :key="p.id"
        class="aba"
        :class="{ ativa: p.id === ativoId }"
        @click="ativoId = p.id!"
      >
        {{ p.nome }}
        <el-dropdown v-if="podeEditar && p.id === ativoId" trigger="click" @command="(c) => menuAba(c, p)">
          <Icon class="ml-4px" icon="ep:arrow-down" :size="12" @click.stop />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="renomear">Renomear</el-dropdown-item>
              <el-dropdown-item command="esquerda">Mover para a esquerda</el-dropdown-item>
              <el-dropdown-item command="direita">Mover para a direita</el-dropdown-item>
              <el-dropdown-item command="excluir" divided>Excluir painel</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <el-tooltip v-if="podeEditar" content="Novo painel" placement="top">
        <div class="aba nova" @click="novoPainel"><Icon icon="ep:plus" /></div>
      </el-tooltip>
    </div>

    <div v-if="filtroGeral" class="filtro-ativo">
      <Icon icon="ep:filter" /> Filtro em todos os quadros: <code>{{ filtroGeral }}</code>
      <el-button link type="primary" @click="limparFiltro">limpar</el-button>
    </div>

    <!-- quadros -->
    <div v-if="ativo" class="grade">
      <Quadro
        v-for="(q, i) in ativo.widgets"
        :key="q.id"
        :quadro="q"
        :editavel="editando"
        @editar="formRef.abrir(q)"
        @remover="removerQuadro(i)"
        @duplicar="duplicar(i)"
        @largura="mudarLargura(i)"
        @esquerda="mover(i, -1)"
        @direita="mover(i, 1)"
      />
      <div v-if="editando" class="adicionar" @click="formRef.abrir()">
        <Icon icon="ep:plus" :size="22" />
        <span>Adicionar quadro</span>
      </div>
    </div>
    <el-empty
      v-else-if="!carregando"
      description="Nenhum painel ainda"
      :image-size="80"
    />
    <el-empty
      v-if="ativo && !ativo.widgets.length && !editando"
      description="Painel vazio: clique em Editar para adicionar quadros"
      :image-size="80"
    />

    <QuadroForm ref="formRef" @salvar="salvarQuadro" />
    <Gavetas ref="gavetas" />
  </div>
</template>

<script lang="ts" setup>
import { ElMessageBox } from 'element-plus'
import * as Api from '@/api/logs'
import { checkPermi } from '@/utils/permission'
import { CONTEXTO } from '../contexto'
import Gavetas from '../components/Gavetas.vue'
import Quadro from '../components/Quadro.vue'
import QuadroForm from '../components/QuadroForm.vue'

defineOptions({ name: 'LogsPaineis' })

const message = useMessage()
const podeEditar = checkPermi(['logs:painel:editar'])

const paineis = ref<Api.Painel[]>([])
const ativoId = ref<number>()
const ativo = computed(() => paineis.value.find((p) => p.id === ativoId.value))
const carregando = ref(true)
const editando = ref(false)
const formRef = ref()
const gavetas = ref<InstanceType<typeof Gavetas>>()

// filtro geral e período (lembrados neste navegador)
const lerPref = (k: string, padrao: string) => {
  try {
    return localStorage.getItem('coti.logs.' + k) ?? padrao
  } catch {
    return padrao
  }
}
const gravarPref = (k: string, v: string) => {
  try {
    localStorage.setItem('coti.logs.' + k, v)
  } catch {
    /* sem armazenamento */
  }
}
const filtroGeral = ref('')
const filtroDigitado = ref('')
const periodoGeral = ref('')
const atualizacao = ref(0)
const intervalo = ref(Number(lerPref('intervalo', '60')))
watch(intervalo, (v) => gravarPref('intervalo', String(v)))
watch(ativoId, (v) => v && gravarPref('painel', String(v)))

const aplicar = () => {
  filtroGeral.value = filtroDigitado.value.trim()
}
const limparFiltro = () => {
  filtroDigitado.value = ''
  aplicar()
}
const atualizar = () => atualizacao.value++

provide(CONTEXTO, {
  filtroGeral,
  periodoGeral,
  atualizacao,
  filtrar: (campo, valor, excluir) => {
    const t = (excluir ? '-' : '') + Api.termo(campo, valor)
    filtroDigitado.value = filtroGeral.value ? `${filtroGeral.value} AND ${t}` : t
    aplicar()
  },
  mostrarMensagens: (titulo, filtro) => gavetas.value?.mostrarMensagens(titulo, filtro),
  abrirMensagem: (m) => gavetas.value?.abrirMensagem(m)
})

// atualização automática (pausa com a aba do navegador escondida)
let timer: ReturnType<typeof setInterval> | undefined
watch(
  intervalo,
  (s) => {
    if (timer) clearInterval(timer)
    timer = s ? setInterval(() => !document.hidden && atualizar(), s * 1000) : undefined
  },
  { immediate: true }
)
onBeforeUnmount(() => timer && clearInterval(timer))

const carregar = async () => {
  carregando.value = true
  try {
    paineis.value = await Api.getPaineis()
    const lembrado = Number(lerPref('painel', '0'))
    if (!paineis.value.some((p) => p.id === ativoId.value)) {
      ativoId.value = paineis.value.some((p) => p.id === lembrado) ? lembrado : paineis.value[0]?.id
    }
  } finally {
    carregando.value = false
  }
}
onMounted(carregar)

// ================================================================================== edição

const salvar = async (p: Api.Painel) => {
  try {
    await Api.salvarPainel(p)
  } catch {
    await carregar() // desfaz o que não foi aceito
  }
}

const salvarQuadro = (q: Api.Quadro) => {
  const p = ativo.value
  if (!p) return
  const i = p.widgets.findIndex((w) => w.id === q.id)
  if (i >= 0) p.widgets.splice(i, 1, q)
  else p.widgets.push(q)
  salvar(p)
}

const removerQuadro = async (i: number) => {
  const p = ativo.value!
  await message.confirm(`Excluir o quadro "${p.widgets[i].titulo}"?`)
  p.widgets.splice(i, 1)
  salvar(p)
}

const duplicar = (i: number) => {
  const p = ativo.value!
  const copia = { ...JSON.parse(JSON.stringify(p.widgets[i])), id: 'q' + Date.now().toString(36) }
  copia.titulo = copia.titulo + ' (cópia)'
  p.widgets.splice(i + 1, 0, copia)
  salvar(p)
}

const mudarLargura = (i: number) => {
  const p = ativo.value!
  const q = p.widgets[i]
  q.largura = ((q.largura || 1) % 4) + 1
  salvar(p)
}

const mover = (i: number, d: number) => {
  const p = ativo.value!
  const j = i + d
  if (j < 0 || j >= p.widgets.length) return
  const [q] = p.widgets.splice(i, 1)
  p.widgets.splice(j, 0, q)
  salvar(p)
}

const novoPainel = async () => {
  const { value } = await ElMessageBox.prompt('Nome do painel', 'Novo painel', {
    inputPattern: /^.{1,60}$/,
    inputErrorMessage: 'De 1 a 60 letras'
  })
  const id = await Api.salvarPainel({ nome: value.trim(), widgets: [] })
  await carregar()
  ativoId.value = id
  editando.value = true
}

const menuAba = async (cmd: string, p: Api.Painel) => {
  if (cmd === 'renomear') {
    const { value } = await ElMessageBox.prompt('Nome do painel', 'Renomear', {
      inputValue: p.nome,
      inputPattern: /^.{1,60}$/,
      inputErrorMessage: 'De 1 a 60 letras'
    })
    p.nome = value.trim()
    salvar(p)
  } else if (cmd === 'excluir') {
    await message.confirm(`Excluir o painel "${p.nome}" e todos os quadros dele?`)
    await Api.excluirPainel(p.id!)
    ativoId.value = undefined
    await carregar()
  } else {
    const i = paineis.value.indexOf(p)
    const j = i + (cmd === 'esquerda' ? -1 : 1)
    if (j < 0 || j >= paineis.value.length) return
    paineis.value.splice(i, 1)
    paineis.value.splice(j, 0, p)
    await Api.ordenarPaineis(paineis.value.map((x) => x.id!))
  }
}
</script>

<style lang="scss" scoped>
.paineis {
  padding-bottom: 20px;
}

.filtro {
  display: flex;
  margin-bottom: 10px;
  flex-wrap: wrap;
  gap: 8px;

  .aplicar {
    width: 52px;
  }

  .consulta {
    min-width: 280px;
    font-family: Consolas, 'Courier New', monospace;
    flex: 1;
  }
}

.abas {
  display: flex;
  margin-bottom: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-wrap: wrap;
  align-items: flex-end;
}

.aba {
  display: inline-flex;
  padding: 9px 16px;
  margin-bottom: -1px;
  font-size: 14px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  border: 1px solid transparent;
  border-radius: 6px 6px 0 0;
  align-items: center;

  &:hover {
    color: var(--el-color-primary);
  }

  &.ativa {
    font-weight: 600;
    color: var(--el-text-color-primary);
    background: var(--el-bg-color-overlay);
    border-color: var(--el-border-color-lighter);
    border-bottom-color: var(--el-bg-color-overlay);
  }

  &.nova {
    padding: 9px 12px;
  }
}

.filtro-ativo {
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--el-text-color-secondary);

  code {
    padding: 1px 5px;
    color: var(--el-color-success);
    background: var(--el-fill-color);
    border-radius: 3px;
  }
}

.grade {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.grade :deep(.largura-2) {
  grid-column: span 2;
}

.grade :deep(.largura-3) {
  grid-column: span 3;
}

.grade :deep(.largura-4) {
  grid-column: span 4;
}

@media (width <= 1200px) {
  .grade {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .grade :deep(.largura-3),
  .grade :deep(.largura-4) {
    grid-column: span 2;
  }
}

@media (width <= 700px) {
  .grade {
    grid-template-columns: minmax(0, 1fr);
  }

  .grade :deep(.quadro) {
    grid-column: span 1;
  }
}

.adicionar {
  display: flex;
  min-height: 150px;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  border: 2px dashed var(--el-border-color);
  border-radius: 6px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;

  &:hover {
    color: var(--el-color-primary);
    border-color: var(--el-color-primary);
  }
}
</style>
