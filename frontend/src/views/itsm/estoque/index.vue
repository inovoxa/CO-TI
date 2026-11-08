<template>
  <ContentWrap>
    <div class="resumo">
      <div v-for="r in resumo" :key="r.tipoId" class="cartao" @click="filtrarTipo(r.tipoId)">
        <div class="font-bold">{{ r.tipo }}</div>
        <div class="text-12px text-[var(--el-text-color-secondary)]">
          {{ r.disponivel }} disponíveis · {{ r.emUso }} em uso · {{ r.manutencao }} em manutenção
        </div>
        <el-tag v-if="r.baixos" class="mt-4px" disable-transitions size="small" type="danger">
          {{ r.baixos }} no mínimo ou abaixo
        </el-tag>
      </div>
    </div>
  </ContentWrap>

  <ContentWrap>
    <div class="flex flex-wrap items-center gap-8px">
      <el-input
        v-model="q.busca"
        class="!w-280px"
        clearable
        placeholder="Descrição, IMEI, linha, patrimônio, responsável"
        @clear="buscar"
        @keyup.enter="buscar"
      >
        <template #prefix><Icon icon="ep:search" /></template>
      </el-input>
      <el-select
        v-model="q.tipoId"
        class="!w-180px"
        clearable
        placeholder="Todos os tipos"
        @change="buscar"
      >
        <el-option v-for="t in tipos" :key="t.id" :label="t.nome" :value="t.id!" />
      </el-select>
      <el-select
        v-model="q.situacao"
        class="!w-170px"
        clearable
        placeholder="Toda situação"
        @change="buscar"
      >
        <el-option v-for="(r, s) in SITUACOES" :key="s" :label="r.rotulo" :value="s" />
      </el-select>
      <el-select
        v-model="q.condicao"
        class="!w-150px"
        clearable
        placeholder="Novo e usado"
        @change="buscar"
      >
        <el-option label="Novo" value="novo" />
        <el-option label="Usado" value="usado" />
      </el-select>
      <el-checkbox v-model="q.baixo" @change="buscar">Estoque baixo</el-checkbox>
      <el-checkbox v-model="q.garantia" @change="buscar">Garantia vencendo (90 dias)</el-checkbox>
      <div class="flex-1"></div>
      <el-button plain @click="baixarPlanilha"><Icon class="mr-4px" icon="ep:download" /> Excel</el-button>
      <el-button plain @click="imprimirLista"><Icon class="mr-4px" icon="ep:printer" /> PDF</el-button>
      <el-button v-hasPermi="['itsm:estoque:update']" plain @click="abrirTipo">
        <Icon class="mr-4px" icon="ep:collection-tag" /> Novo tipo
      </el-button>
      <el-button v-hasPermi="['itsm:estoque:update']" plain type="primary" @click="abrirItem()">
        <Icon class="mr-4px" icon="ep:plus" /> Novo item
      </el-button>
    </div>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="lista">
      <el-table-column label="Item" min-width="240">
        <template #default="{ row }">
          <div class="font-bold">{{ row.descricao }}</div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">
            {{ row.tipo }}
            <template v-if="row.identificador"> · {{ row.identificador }}</template>
            <template v-if="row.linha"> · linha {{ row.linha }}</template>
            <template v-if="row.patrimonio"> · pat. {{ row.patrimonio }}</template>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="Condição" width="100">
        <template #default="{ row }">
          <el-tag
            disable-transitions
            effect="plain"
            size="small"
            :type="row.condicao === 'novo' ? 'success' : 'info'"
            >{{ row.condicao === 'novo' ? 'Novo' : 'Usado' }}</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="Situação" width="140">
        <template #default="{ row }">
          <el-tag disable-transitions size="small" :type="SITUACOES[row.situacao].tipo">{{
            SITUACOES[row.situacao].rotulo
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Saldo" width="90">
        <template #default="{ row }">
          <span v-if="row.controle === 'quantidade'" :class="{ 'text-red-500': row.baixo }">{{
            row.quantidade
          }}</span>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="Com quem está / origem" min-width="210">
        <template #default="{ row }">
          <div v-if="row.responsavel">
            {{ row.responsavel }}<template v-if="row.destino"> ({{ row.destino }})</template>
          </div>
          <div class="text-12px text-[var(--el-text-color-secondary)]">
            <template v-if="row.condicao === 'novo'">
              <template v-if="row.dataCompra">Comprado em {{ data(row.dataCompra) }}</template>
              <template v-if="row.fornecedor"> · {{ row.fornecedor }}</template>
            </template>
            <template v-else-if="row.usadoPor">Usado por {{ row.usadoPor }}</template>
            <template v-if="row.garantiaAte"> · garantia até {{ data(row.garantiaAte) }}</template>
          </div>
        </template>
      </el-table-column>
      <el-table-column align="right" label="Ações" width="240">
        <template #default="{ row }">
          <el-dropdown
            v-if="movimentos(row).length"
            v-hasPermi="['itsm:estoque:update']"
            class="mr-8px"
            @command="(m: string) => abrirMovimento(row, m)"
          >
            <el-button link type="primary">Movimentar</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="m in movimentos(row)" :key="m" :command="m">{{
                  MOVIMENTOS[m]
                }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button link @click="abrirHistorico(row)">Histórico</el-button>
          <el-button v-hasPermi="['itsm:estoque:update']" link @click="abrirItem(row)"
            >Editar</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="q.pageSize"
      v-model:page="q.pageNo"
      :total="total"
      @pagination="carregar"
    />
  </ContentWrap>

  <Dialog v-model="item.visivel" :title="item.dados.id ? 'Editar item' : 'Novo item'" width="680px">
    <el-form ref="itemRef" :model="item.dados" :rules="regras" label-position="top">
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="Tipo" prop="tipoId">
            <el-select v-model="item.dados.tipoId" :disabled="!!item.dados.id" class="w-full">
              <el-option
                v-for="t in tipos.filter((x) => x.ativo)"
                :key="t.id"
                :label="t.nome"
                :value="t.id!"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Condição" prop="condicao">
            <el-radio-group v-model="item.dados.condicao">
              <el-radio value="novo">Novo</el-radio>
              <el-radio value="usado">Usado</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="Descrição" prop="descricao">
        <el-input
          v-model="item.dados.descricao"
          maxlength="200"
          placeholder="Ex.: Samsung A15 128 GB, DDR4 8 GB"
        />
      </el-form-item>
      <el-row v-if="porUnidade" :gutter="12">
        <el-col :span="10">
          <el-form-item label="IMEI, ICCID ou número de série">
            <el-input v-model="item.dados.identificador" maxlength="80" />
          </el-form-item>
        </el-col>
        <el-col :span="7">
          <el-form-item label="Linha">
            <el-input v-model="item.dados.linha" maxlength="20" />
          </el-form-item>
        </el-col>
        <el-col :span="7">
          <el-form-item label="Patrimônio">
            <el-input v-model="item.dados.patrimonio" maxlength="50" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row v-else :gutter="12">
        <el-col v-if="!item.dados.id" :span="12">
          <el-form-item label="Quantidade inicial">
            <el-input-number v-model="item.dados.quantidade" :min="0" class="!w-full" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Avisar quando chegar a">
            <el-input-number v-model="item.dados.minimo" :min="0" class="!w-full" />
          </el-form-item>
        </el-col>
      </el-row>
      <template v-if="item.dados.condicao === 'novo'">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="Data da compra">
              <el-date-picker
                v-model="item.dados.dataCompra"
                class="!w-full"
                format="DD/MM/YYYY"
                type="date"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Nota fiscal">
              <el-input v-model="item.dados.notaFiscal" maxlength="60" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="Valor (R$)">
              <el-input-number
                v-model="item.dados.valor"
                :controls="false"
                :min="0"
                :precision="2"
                class="!w-full"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Fornecedor">
          <el-input v-model="item.dados.fornecedor" maxlength="150" />
        </el-form-item>
      </template>
      <el-form-item v-else label="Quem usava antes">
        <el-input v-model="item.dados.usadoPor" maxlength="150" />
      </el-form-item>
      <el-form-item label="Garantia até">
        <el-date-picker
          v-model="item.dados.garantiaAte"
          format="DD/MM/YYYY"
          type="date"
          value-format="YYYY-MM-DD"
        />
      </el-form-item>
      <el-form-item label="Observação">
        <el-input v-model="item.dados.observacao" maxlength="1000" type="textarea" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :loading="salvando" type="primary" @click="salvarItem">Salvar</el-button>
      <el-button @click="item.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>

  <Dialog v-model="mov.visivel" :title="MOVIMENTOS[mov.tipo]" width="480px">
    <div class="mb-12px font-bold">{{ mov.item?.descricao }}</div>
    <el-form label-position="top">
      <el-form-item
        v-if="mov.item?.controle === 'quantidade'"
        :label="`Quantidade (saldo: ${mov.item?.quantidade})`"
      >
        <el-input-number v-model="mov.quantidade" :min="1" />
      </el-form-item>
      <template v-if="pedePessoa">
        <el-form-item label="Pessoa" required>
          <el-input v-model="mov.pessoa" maxlength="150" placeholder="Quem retira ou devolve" />
        </el-form-item>
        <el-form-item label="Setor ou unidade">
          <el-input v-model="mov.destino" maxlength="150" />
        </el-form-item>
      </template>
      <el-form-item :label="pedeMotivo ? 'Motivo' : 'Observação'" :required="pedeMotivo">
        <el-input v-model="mov.motivo" maxlength="500" type="textarea" />
      </el-form-item>
      <el-form-item label="Número do chamado (opcional)">
        <el-input-number v-model="mov.chamadoId" :controls="false" :min="1" class="!w-160px" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :loading="salvando" type="primary" @click="confirmarMovimento"
        >Registrar</el-button
      >
      <el-button @click="mov.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>

  <Dialog v-model="hist.visivel" :title="`Histórico: ${hist.item?.descricao}`" width="1040px">
    <el-table v-loading="hist.loading" :data="hist.lista">
      <el-table-column label="Quando" width="150">
        <template #default="{ row }">{{ dataHora(row.quando) }}</template>
      </el-table-column>
      <el-table-column label="Movimento" width="120">
        <template #default="{ row }">{{ MOVIMENTOS[row.tipo] }}</template>
      </el-table-column>
      <el-table-column label="Qtd." prop="quantidade" width="60" />
      <el-table-column label="Pessoa" min-width="150">
        <template #default="{ row }">
          {{ row.pessoa }}<template v-if="row.destino"> ({{ row.destino }})</template>
        </template>
      </el-table-column>
      <el-table-column label="Motivo" min-width="160" prop="motivo" />
      <el-table-column label="Chamado" width="95">
        <template #default="{ row }">{{ row.chamadoId ? `#${row.chamadoId}` : '' }}</template>
      </el-table-column>
      <el-table-column label="Registrado por" prop="registradoPor" width="130" />
      <el-table-column fixed="right" label="" width="70">
        <template #default="{ row }">
          <el-button
            v-if="row.tipo === 'retirada' || row.tipo === 'devolucao'"
            link
            type="primary"
            @click="imprimirTermo(row)"
            >Termo</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </Dialog>

  <Dialog v-model="tipo.visivel" title="Novo tipo" width="420px">
    <el-form label-position="top">
      <el-form-item label="Nome" required>
        <el-input v-model="tipo.nome" maxlength="80" />
      </el-form-item>
      <el-form-item label="Como controlar" required>
        <el-radio-group v-model="tipo.controle">
          <el-radio value="unidade">Cada peça com identificação</el-radio>
          <el-radio value="quantidade">Só a quantidade</el-radio>
        </el-radio-group>
        <div class="text-12px text-[var(--el-text-color-secondary)]">
          Não muda depois de criado.
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :loading="salvando" type="primary" @click="salvarTipo">Salvar</el-button>
      <el-button @click="tipo.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import type { FormRules } from 'element-plus'
import download from '@/utils/download'
import * as Api from '@/api/itsm/estoque'
import { imprimirHtml, termoHtml, relatorioHtml } from './impressao'

/** Estoque da GTI: situação e saldo só mudam por movimento, que fica no histórico do item. */
defineOptions({ name: 'ItsmEstoque' })
const message = useMessage()

const SITUACOES: Record<string, { rotulo: string; tipo: 'success' | 'primary' | 'warning' | 'info' }> =
  {
    disponivel: { rotulo: 'Disponível', tipo: 'success' },
    em_uso: { rotulo: 'Em uso', tipo: 'primary' },
    manutencao: { rotulo: 'Em manutenção', tipo: 'warning' },
    baixado: { rotulo: 'Baixado', tipo: 'info' }
  }
const MOVIMENTOS: Record<string, string> = {
  entrada: 'Entrada',
  retirada: 'Retirada',
  devolucao: 'Devolução',
  manutencao: 'Enviar para manutenção',
  retorno: 'Retorno da manutenção',
  baixa: 'Baixa (perda, furto, descarte)'
}

const loading = ref(true)
const salvando = ref(false)
const lista = ref<Api.Item[]>([])
const total = ref(0)
const tipos = ref<Api.Tipo[]>([])
const resumo = ref<Api.Resumo[]>([])
const q = reactive<any>({
  pageNo: 1,
  pageSize: 20,
  busca: '',
  tipoId: undefined,
  situacao: undefined,
  condicao: undefined,
  baixo: false,
  garantia: false
})

const data = (d?: string) => (d ? d.split('-').reverse().join('/') : '')
const dataHora = (d: any) => (d ? new Date(d).toLocaleString('pt-BR').slice(0, 17) : '')

const filtros = () => {
  const { garantia, ...resto } = q
  return { ...resto, baixo: q.baixo || undefined, garantiaDias: garantia ? 90 : undefined }
}
const baixarPlanilha = async () => download.excel(await Api.exportar(filtros()), 'estoque-gti.xlsx')
const imprimirLista = async () => imprimirHtml(relatorioHtml(await Api.getRelatorio(filtros())))
const imprimirTermo = (m: Api.Movimento) => imprimirHtml(termoHtml(m))

const carregar = async () => {
  loading.value = true
  try {
    const r = await Api.getPagina(filtros())
    lista.value = r.list
    total.value = r.total
  } finally {
    loading.value = false
  }
}
const buscar = () => {
  q.pageNo = 1
  carregar()
}
const atualizar = async () => {
  resumo.value = await Api.getResumo()
  await carregar()
}
const filtrarTipo = (id: number) => {
  q.tipoId = id
  buscar()
}

/** O servidor confere de novo; aqui só se oferece o que vale para a situação do item. */
const movimentos = (i: Api.Item): string[] => {
  if (i.situacao === 'baixado') return []
  if (i.controle === 'quantidade') return ['entrada', 'retirada', 'devolucao', 'baixa']
  return {
    disponivel: ['retirada', 'manutencao', 'baixa'],
    em_uso: ['devolucao', 'manutencao', 'baixa'],
    manutencao: ['retorno', 'baixa']
  }[i.situacao as 'disponivel' | 'em_uso' | 'manutencao']
}

const itemRef = ref()
const vazio = (): Api.Item => ({ descricao: '', condicao: 'novo', quantidade: 0, minimo: 0 })
const item = reactive({ visivel: false, dados: vazio() })
const regras: FormRules = {
  tipoId: [{ required: true, message: 'Escolha o tipo', trigger: 'change' }],
  descricao: [{ required: true, message: 'Informe a descrição', trigger: 'blur' }]
}
const porUnidade = computed(
  () => tipos.value.find((t) => t.id === item.dados.tipoId)?.controle !== 'quantidade'
)
const abrirItem = (i?: Api.Item) => {
  item.dados = i ? { ...i } : vazio()
  item.visivel = true
  nextTick(() => itemRef.value?.clearValidate())
}
const salvarItem = async () => {
  if (!(await itemRef.value.validate().catch(() => false))) return
  salvando.value = true
  try {
    await Api.salvar(item.dados)
    item.visivel = false
    message.success('Item salvo')
    await atualizar()
  } finally {
    salvando.value = false
  }
}

const mov = reactive<{
  visivel: boolean
  item?: Api.Item
  tipo: string
  quantidade: number
  pessoa: string
  destino: string
  motivo: string
  chamadoId?: number
}>({ visivel: false, tipo: 'retirada', quantidade: 1, pessoa: '', destino: '', motivo: '' })
const pedePessoa = computed(
  () =>
    mov.tipo === 'retirada' || (mov.tipo === 'devolucao' && mov.item?.controle === 'quantidade')
)
const pedeMotivo = computed(() => mov.tipo === 'baixa' || mov.tipo === 'manutencao')
const abrirMovimento = (i: Api.Item, tipoMov: string) => {
  Object.assign(mov, {
    visivel: true,
    item: i,
    tipo: tipoMov,
    quantidade: 1,
    pessoa: '',
    destino: '',
    motivo: '',
    chamadoId: undefined
  })
}
const confirmarMovimento = async () => {
  salvando.value = true
  try {
    await Api.movimentar({
      itemId: mov.item!.id!,
      tipo: mov.tipo,
      quantidade: mov.quantidade,
      pessoa: mov.pessoa,
      destino: mov.destino,
      motivo: mov.motivo,
      chamadoId: mov.chamadoId
    })
    mov.visivel = false
    message.success('Movimento registrado')
    await atualizar()
  } finally {
    salvando.value = false
  }
}

const hist = reactive<{ visivel: boolean; loading: boolean; item?: Api.Item; lista: Api.Movimento[] }>(
  { visivel: false, loading: false, lista: [] }
)
const abrirHistorico = async (i: Api.Item) => {
  Object.assign(hist, { visivel: true, loading: true, item: i, lista: [] })
  try {
    hist.lista = await Api.getHistorico(i.id!)
  } finally {
    hist.loading = false
  }
}

const tipo = reactive<{ visivel: boolean; nome: string; controle: 'unidade' | 'quantidade' }>({
  visivel: false,
  nome: '',
  controle: 'unidade'
})
const abrirTipo = () => Object.assign(tipo, { visivel: true, nome: '', controle: 'unidade' })
const salvarTipo = async () => {
  salvando.value = true
  try {
    await Api.salvarTipo({ nome: tipo.nome, controle: tipo.controle })
    tipo.visivel = false
    message.success('Tipo criado')
    tipos.value = await Api.getTipos()
    resumo.value = await Api.getResumo()
  } finally {
    salvando.value = false
  }
}

onMounted(async () => {
  tipos.value = await Api.getTipos()
  await atualizar()
})
</script>

<style scoped>
.resumo {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
}

.cartao {
  padding: 10px 12px;
  cursor: pointer;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.cartao:hover {
  border-color: var(--el-color-primary);
}
</style>
