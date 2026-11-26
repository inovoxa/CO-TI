<!-- Rota: /monitoramento/unidades · Permissões: monitoramento:monitor:query (ver) e monitoramento:unidade:update -->
<template>
  <ContentWrap>
    <div class="mb-12px flex items-center justify-between">
      <span class="text-13px text-[var(--el-text-color-secondary)]">
        Unidades (filiais) que aparecem no mapa do telão. O CEP preenche o endereço; com o número, o
        ponto é calculado e pode ser ajustado no mapa.
      </span>
      <div class="flex gap-8px">
        <el-button plain @click="exportar">
          <Icon class="mr-4px" icon="ep:download" /> Exportar
        </el-button>
        <el-button v-if="pode" plain @click="importador?.abrir()">
          <Icon class="mr-4px" icon="ep:upload" /> Importar
        </el-button>
        <el-button v-if="pode" type="primary" @click="abrir()"
          ><Icon class="mr-4px" icon="ep:plus" />Nova unidade</el-button
        >
      </div>
    </div>
    <div class="mb-12px flex flex-wrap items-center gap-8px">
      <el-input
        v-model="q.busca"
        class="!w-300px"
        clearable
        placeholder="Nome, departamento, endereço ou CEP"
      >
        <template #prefix><Icon icon="ep:search" /></template>
      </el-input>
      <el-check-tag
        v-for="c in SITUACOES"
        :key="c.valor"
        :checked="q.situacao === c.valor"
        @change="q.situacao = c.valor"
      >
        {{ c.rotulo }} <b class="ml-2px">{{ contagens[c.valor] }}</b>
      </el-check-tag>
    </div>
    <el-table
      v-loading="loading"
      :data="pagina"
      :default-sort="{ prop: 'nome', order: 'ascending' }"
      @sort-change="ordenar"
    >
      <el-table-column label="Unidade" min-width="200" prop="nome" sortable="custom">
        <template #default="{ row }">
          <span class="font-medium">{{ row.nome }}</span>
          <el-tag v-if="!row.ativa" class="ml-6px" disable-transitions size="small" type="info"
            >inativa</el-tag
          >
          <el-tooltip
            v-if="parecidas.has(row.id)"
            :content="`Parece a mesma que: ${textoParecidas(row)}`"
            placement="top"
          >
            <el-tag class="ml-6px" disable-transitions size="small" type="warning"
              >possível repetida</el-tag
            >
          </el-tooltip>
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.deptNome }}</div>
        </template>
      </el-table-column>
      <el-table-column v-if="veFaixas" label="Faixas de IP" min-width="170">
        <template #default="{ row }">
          <el-tag
            v-for="f in faixasDa(row.deptId)"
            :key="f.id"
            class="mb-2px mr-4px"
            disable-transitions
            size="small"
            type="info"
            >{{ f.cidr }}</el-tag
          >
          <span v-if="!faixasDa(row.deptId).length" class="text-12px text-[var(--el-text-color-placeholder)]"
            >nenhuma</span
          >
        </template>
      </el-table-column>
      <el-table-column label="Endereço" min-width="280" prop="cidade" sortable="custom">
        <template #default="{ row }">{{ endereco(row) || '—' }}</template>
      </el-table-column>
      <el-table-column label="Coordenadas" width="190">
        <template #default="{ row }">
          <span v-if="temPonto(row)" class="text-12px"
            >{{ Number(row.latitude).toFixed(5) }}, {{ Number(row.longitude).toFixed(5) }}</span
          >
          <span v-else class="text-12px text-[var(--el-color-warning)]">sem localização</span>
        </template>
      </el-table-column>
      <el-table-column
        align="right"
        label="Monitores"
        prop="monitores"
        sortable="custom"
        width="120"
      />
      <el-table-column v-if="pode" label="" width="190">
        <template #default="{ row }">
          <el-button link type="primary" @click="abrir(row)">Editar</el-button>
          <el-button
            link
            :type="parecidas.has(row.id) ? 'warning' : 'primary'"
            @click="abrirJuntar(row)"
            >Juntar</el-button
          >
          <el-button link type="danger" @click="excluir(row)">Excluir</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!loading && !filtradas.length"
      :image-size="60"
      :description="
        lista.length ? 'Nenhuma unidade encontrada com esse filtro.' : 'Nenhuma unidade cadastrada.'
      "
    />
    <Pagination v-model:limit="q.pageSize" v-model:page="q.pageNo" :total="filtradas.length" />
  </ContentWrap>

  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Alterar unidade' : 'Nova unidade'"
    width="760px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="120px">
      <el-form-item label="Nome" prop="nome">
        <el-input v-model="form.dados.nome" maxlength="100" />
        <el-alert
          v-if="noForm.mesmoNome"
          :closable="false"
          :title="
            noForm.bloqueia
              ? `Já existe a unidade “${noForm.mesmoNome.nome}”: use outro nome.`
              : `Existe outra unidade com este nome (“${noForm.mesmoNome.nome}”). Se for a mesma, use Juntar na lista.`
          "
          :type="noForm.bloqueia ? 'error' : 'warning'"
          class="mt-6px"
          show-icon
        />
        <el-alert
          v-if="noForm.parecidas.length"
          :closable="false"
          class="mt-6px"
          show-icon
          title="Confira se não é a mesma unidade:"
          type="warning"
        >
          <div v-for="p in noForm.parecidas" :key="p.unidade.id">
            <b>{{ p.unidade.nome }}</b> — {{ p.motivos.join(', ') }}
            <span v-if="endereco(p.unidade)" class="text-[var(--el-text-color-secondary)]"
              >· {{ endereco(p.unidade) }}</span
            >
          </div>
        </el-alert>
      </el-form-item>
      <el-form-item label="Departamento" prop="deptId">
        <el-tree-select
          v-model="form.dados.deptId"
          :data="depts"
          :props="defaultProps"
          check-strictly
          class="w-full"
          node-key="id"
          placeholder="departamento raiz da unidade"
        />
      </el-form-item>
      <el-form-item label="CEP">
        <el-input
          v-model="form.dados.cep"
          class="!w-150px"
          maxlength="9"
          placeholder="00000-000"
          @keyup.enter="buscarCep"
        />
        <el-button :loading="buscando" class="ml-8px" @click="buscarCep">Buscar endereço</el-button>
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="16">
          <el-form-item label="Logradouro"
            ><el-input v-model="form.dados.logradouro" maxlength="200"
          /></el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Número" label-width="70px"
            ><el-input v-model="form.dados.numero" maxlength="20"
          /></el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="Complemento"
            ><el-input v-model="form.dados.complemento" maxlength="100"
          /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Bairro" label-width="70px"
            ><el-input v-model="form.dados.bairro" maxlength="100"
          /></el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="16">
          <el-form-item label="Cidade"
            ><el-input v-model="form.dados.cidade" maxlength="100"
          /></el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="UF" label-width="70px"
            ><el-input v-model="form.dados.uf" maxlength="2"
          /></el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="Localização" prop="latitude">
        <div class="w-full">
          <div class="mb-8px flex flex-wrap items-center gap-8px">
            <el-button :loading="localizando" @click="localizar">Localizar no mapa</el-button>
            <el-input-number
              v-model="form.dados.latitude"
              :controls="false"
              :precision="7"
              class="!w-140px"
              placeholder="latitude"
            />
            <el-input-number
              v-model="form.dados.longitude"
              :controls="false"
              :precision="7"
              class="!w-140px"
              placeholder="longitude"
            />
          </div>
          <el-alert
            v-if="aviso"
            :closable="false"
            :title="aviso"
            class="mb-8px"
            show-icon
            type="warning"
          />
          <MapaPonto
            v-if="form.visivel"
            ref="mapa"
            :latitude="form.dados.latitude"
            :longitude="form.dados.longitude"
            @mover="
              (lat, lng) => {
                form.dados.latitude = lat
                form.dados.longitude = lng
                aviso = ''
              }
            "
          />
          <div class="mt-4px text-12px text-[var(--el-text-color-secondary)]"
            >Clique no mapa ou arraste o ponto para ajustar.</div
          >
        </div>
      </el-form-item>
      <el-form-item label="">
        <el-checkbox v-model="form.dados.ativa">Ativa (aparece no telão)</el-checkbox>
      </el-form-item>
      <el-form-item v-if="veFaixas" label="Faixas de IP">
        <div class="w-full">
          <template v-if="form.dados.id && form.dados.deptId">
            <div>
              <el-tag
                v-for="f in faixasDa(form.dados.deptId)"
                :key="f.id"
                :closable="editaFaixas"
                class="mb-4px mr-6px"
                disable-transitions
                @close="excluirFaixa(f)"
                >{{ f.cidr }} · {{ f.maquinas }} máquina(s)</el-tag
              >
              <span v-if="!faixasDa(form.dados.deptId).length" class="text-12px text-[var(--el-text-color-secondary)]"
                >nenhuma faixa: as máquinas desta unidade ficam sem unidade no inventário</span
              >
            </div>
            <div v-if="editaFaixas" class="mt-4px flex flex-wrap items-center gap-6px">
              <el-input
                v-model="novaFaixa"
                class="!w-220px"
                placeholder="172.21.9.0/24 ou um IP"
                @keyup.enter="adicionarFaixa"
              />
              <el-button :disabled="!novaFaixa" @click="adicionarFaixa">Adicionar</el-button>
              <el-button link type="primary" @click="aplicarFaixas">Aplicar nos ativos agora</el-button>
            </div>
          </template>
          <div class="mt-2px text-12px text-[var(--el-text-color-secondary)]">
            A unidade de uma máquina no inventário é a da faixa do primeiro IP dela que cai numa faixa
            (sem faixa, fica sem unidade). "172.21.9.0" vale como /24; um IP sozinho vale só para ele.
            <template v-if="!form.dados.id">Salve a unidade para cadastrar as faixas.</template>
          </div>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>

  <Dialog v-model="juntar.visivel" title="Juntar unidades repetidas" width="620px">
    <p class="mt-0">
      Tudo o que está ligado a <b>{{ juntar.origem.nome }}</b> passa para a unidade escolhida
      abaixo, e <b>{{ juntar.origem.nome }}</b> é excluída.
    </p>
    <el-form label-width="110px">
      <el-form-item label="Fica a unidade">
        <el-select
          v-model="juntar.destinoId"
          class="w-full"
          filterable
          placeholder="escolha a unidade que fica"
        >
          <el-option
            v-for="o in opcoesDestino"
            :key="o.unidade.id"
            :label="o.unidade.nome"
            :value="o.unidade.id!"
          >
            <span>{{ o.unidade.nome }}</span>
            <span v-if="o.dica" class="ml-8px text-12px text-[var(--el-color-warning)]">{{
              o.dica
            }}</span>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>
    <el-descriptions v-if="destino" :column="1" border size="small">
      <el-descriptions-item label="Sai (excluída)">
        <b>{{ juntar.origem.nome }}</b>
        <div class="text-12px text-[var(--el-text-color-secondary)]">{{
          endereco(juntar.origem) || 'sem endereço'
        }}</div>
      </el-descriptions-item>
      <el-descriptions-item label="Fica">
        <b>{{ destino.nome }}</b>
        <div class="text-12px text-[var(--el-text-color-secondary)]">{{
          endereco(destino) || 'sem endereço'
        }}</div>
      </el-descriptions-item>
      <el-descriptions-item label="Passam para a que fica">
        {{ ligados(juntar.origem) || 'nada (a unidade que sai não tem vínculos)' }}
      </el-descriptions-item>
    </el-descriptions>
    <p v-if="destino" class="mb-0 text-12px text-[var(--el-text-color-secondary)]">
      Nome, endereço e ponto no mapa de <b>{{ destino.nome }}</b> não mudam. Se o certo estiver na
      outra, use “Trocar”. A operação fica registrada na auditoria.
    </p>
    <template #footer>
      <el-button :disabled="!destino" @click="inverterJuntar">
        <Icon class="mr-4px" icon="ep:switch" />Trocar qual fica
      </el-button>
      <el-button
        :disabled="!destino"
        :loading="juntar.salvando"
        type="danger"
        @click="confirmarJuntar"
        >Juntar</el-button
      >
      <el-button @click="juntar.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>

  <ImportarPlanilha
    ref="importador"
    :analisar="Mon.analisarImportacaoUnidades"
    :aplicar="Mon.importarUnidades"
    nome-plural="unidades"
    nome-singular="unidade"
    @importado="carregar"
  />
</template>

<script lang="ts" setup>
import * as Mon from '@/api/monitoramento'
import * as Inv from '@/api/inventario'
import * as DeptApi from '@/api/system/dept'
import { checkPermi } from '@/utils/permission'
import { defaultProps, handleTree } from '@/utils/tree'
import download from '@/utils/download'
import ImportarPlanilha from '@/components/ImportarPlanilha/index.vue'
import MapaPonto from './MapaPonto.vue'
import { chaveNome, grupos, motivos, parecidasPorUnidade, simples, temPonto } from './duplicadas'

defineOptions({ name: 'MonitoramentoUnidade' })
const message = useMessage()
const pode = checkPermi(['monitoramento:unidade:update'])
const regras = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  deptId: [{ required: true, message: 'Escolha o departamento', trigger: 'change' }],
  latitude: [{ required: true, message: 'Localize a unidade no mapa', trigger: 'change' }]
}
const PRECISAO: Record<string, string> = {
  rua: 'Achei a rua, mas não o número: confira e ajuste o ponto no mapa.',
  cep: 'Achei só o CEP: confira e ajuste o ponto no mapa.',
  cidade: 'Achei só a cidade: arraste o ponto até o endereço da unidade.'
}
const exportar = async () => download.excel(await Mon.exportarUnidades(), 'unidades.xlsx')
const lista = ref<Mon.Unidade[]>([])
const loading = ref(true)
const depts = ref<any[]>([])
const formRef = ref()
const mapa = ref<InstanceType<typeof MapaPonto>>()
const importador = ref<InstanceType<typeof ImportarPlanilha>>()
const form = reactive({ visivel: false, dados: {} as Mon.Unidade })
const buscando = ref(false)
const localizando = ref(false)
const aviso = ref('')

const endereco = (u: Mon.Unidade) =>
  [
    [u.logradouro, u.numero].filter(Boolean).join(', '),
    u.bairro,
    [u.cidade, u.uf].filter(Boolean).join('/'),
    u.cep
  ]
    .filter(Boolean)
    .join(' · ')

type Situacao = 'todas' | 'ativas' | 'inativas' | 'semMonitor' | 'semPonto' | 'repetidas'
const SITUACOES: { valor: Situacao; rotulo: string }[] = [
  { valor: 'todas', rotulo: 'Todas' },
  { valor: 'ativas', rotulo: 'Ativas' },
  { valor: 'inativas', rotulo: 'Inativas' },
  { valor: 'semMonitor', rotulo: 'Sem monitores' },
  { valor: 'semPonto', rotulo: 'Sem localização' },
  { valor: 'repetidas', rotulo: 'Possíveis repetidas' }
]
const q = reactive({
  busca: '',
  situacao: 'todas' as Situacao,
  pageNo: 1,
  pageSize: 10,
  ordem: { prop: 'nome', order: 'ascending' as string | null }
})

const parecidas = computed(() => parecidasPorUnidade(lista.value))
const grupoDe = computed(() => grupos(parecidas.value))
const NA_SITUACAO: Record<Situacao, (u: Mon.Unidade) => boolean> = {
  todas: () => true,
  ativas: (u) => !!u.ativa,
  inativas: (u) => !u.ativa,
  semMonitor: (u) => !u.monitores,
  semPonto: (u) => !temPonto(u),
  repetidas: (u) => parecidas.value.has(u.id!)
}
const textoParecidas = (u: Mon.Unidade) =>
  (parecidas.value.get(u.id!) || [])
    .map((p) => `${p.unidade.nome} (${p.motivos.join(', ')})`)
    .join('; ')
const contagens = computed(
  () =>
    Object.fromEntries(
      SITUACOES.map((s) => [s.valor, lista.value.filter(NA_SITUACAO[s.valor]).length])
    ) as Record<Situacao, number>
)

const textoBusca = (u: Mon.Unidade) =>
  simples(
    [u.nome, u.deptNome, endereco(u), u.complemento, (u.cep || '').replace(/\D/g, '')].join(' ')
  )

const filtradas = computed(() => {
  const termos = simples(q.busca).split(/\s+/).filter(Boolean)
  const lista_ = lista.value.filter(
    (u) => NA_SITUACAO[q.situacao](u) && termos.every((t) => textoBusca(u).includes(t))
  )
  const { prop, order } = q.ordem
  const sinal = order === 'descending' ? -1 : 1
  // nas possíveis repetidas, as do mesmo grupo ficam uma embaixo da outra
  const porGrupo = q.situacao === 'repetidas'
  if (!porGrupo && (!prop || !order)) return lista_
  return [...lista_].sort((a, b) => {
    if (porGrupo) {
      const g = (grupoDe.value.get(a.id!) ?? 0) - (grupoDe.value.get(b.id!) ?? 0)
      if (g) return g
      if (!prop || !order) return 0
    }
    const va = (a as any)[prop]
    const vb = (b as any)[prop]
    const r =
      typeof va === 'number' || typeof vb === 'number'
        ? (va || 0) - (vb || 0)
        : String(va || '').localeCompare(String(vb || ''), 'pt-BR', { sensitivity: 'base' })
    return r * sinal
  })
})
const pagina = computed(() =>
  filtradas.value.slice((q.pageNo - 1) * q.pageSize, q.pageNo * q.pageSize)
)
const ordenar = ({ prop, order }: { prop: string; order: string | null }) => {
  q.ordem = { prop, order }
  q.pageNo = 1
}
watch(
  () => [q.busca, q.situacao],
  () => (q.pageNo = 1)
)
// Ao excluir a última da página, volta para a página anterior em vez de mostrar vazio.
watch(filtradas, (f) => {
  const ultima = Math.max(1, Math.ceil(f.length / q.pageSize))
  if (q.pageNo > ultima) q.pageNo = ultima
})

const carregar = async () => {
  loading.value = true
  try {
    const [u, f] = await Promise.all([Mon.getUnidades(), veFaixas ? Inv.getFaixas() : Promise.resolve([])])
    lista.value = u
    faixas.value = f
  } finally {
    loading.value = false
  }
}

// ---------- faixas de IP da unidade (as do inventário, pelo departamento da unidade) ----------
const veFaixas = checkPermi(['inventario:ativo:query'])
const editaFaixas = checkPermi(['inventario:ativo:update'])
const faixas = ref<Inv.FaixaUnidade[]>([])
const novaFaixa = ref('')
const faixasDa = (deptId?: number) => faixas.value.filter((f) => f.deptId === deptId)
const recarregarFaixas = async () => (faixas.value = await Inv.getFaixas())
const adicionarFaixa = async () => {
  if (!novaFaixa.value.trim() || !form.dados.deptId) return
  await Inv.salvarFaixa({ cidr: novaFaixa.value.trim(), deptId: form.dados.deptId })
  novaFaixa.value = ''
  message.success('Faixa cadastrada. Use "Aplicar nos ativos agora" para recalcular as máquinas já inventariadas.')
  await recarregarFaixas()
}
const excluirFaixa = async (f: Inv.FaixaUnidade) => {
  await message.delConfirm(`Excluir a faixa ${f.cidr}? As máquinas dela ficam sem unidade se não caírem em outra faixa.`)
  await Inv.excluirFaixa(f.id)
  await recarregarFaixas()
}
const aplicarFaixas = async () => {
  const n = await Inv.aplicarFaixas()
  message.success(n ? `${n} ativo(s) com a unidade recalculada (fica no histórico de cada um)` : 'Nenhum ativo mudou de unidade')
  await recarregarFaixas()
}
const abrir = async (u?: Mon.Unidade) => {
  if (!depts.value.length) depts.value = handleTree(await DeptApi.getSimpleDeptList())
  form.dados = u
    ? { ...u, latitude: Number(u.latitude), longitude: Number(u.longitude) }
    : { nome: '', ativa: true }
  aviso.value = ''
  form.visivel = true
}
const buscarCep = async () => {
  if (!form.dados.cep) return
  buscando.value = true
  try {
    const e = await Mon.buscarCep(form.dados.cep)
    form.dados.cep = e.cep
    form.dados.logradouro = e.logradouro || form.dados.logradouro
    form.dados.bairro = e.bairro || form.dados.bairro
    form.dados.cidade = e.cidade || form.dados.cidade
    form.dados.uf = e.uf || form.dados.uf
    if (!form.dados.numero)
      message.info('Endereço preenchido. Informe o número e clique em "Localizar no mapa".')
  } finally {
    buscando.value = false
  }
}
const localizar = async () => {
  localizando.value = true
  try {
    const c = await Mon.geocodificar(form.dados)
    form.dados.latitude = Number(c.latitude)
    form.dados.longitude = Number(c.longitude)
    aviso.value = PRECISAO[c.precisao] || ''
    await nextTick()
    mapa.value?.centralizar()
  } finally {
    localizando.value = false
  }
}
/** No formulário: a que tem o mesmo nome (o servidor recusa) e as que parecem ser a mesma unidade. */
const noForm = computed(() => {
  const d = form.dados
  const outras = lista.value.filter((u) => u.id !== d.id)
  const original = lista.value.find((u) => u.id === d.id)
  const chave = chaveNome(d.nome)
  const mesmoNome = chave ? outras.find((u) => chaveNome(u.nome) === chave) : undefined
  return {
    // editar a repetida que já existia sem trocar o nome continua valendo; o caminho para ela é Juntar
    bloqueia: !!mesmoNome && (!original || chaveNome(original.nome) !== chave),
    mesmoNome,
    parecidas: outras
      .filter((u) => u !== mesmoNome)
      .map((u) => ({ unidade: u, motivos: motivos(d, u, true) }))
      .filter((p) => p.motivos.length)
      .slice(0, 5)
  }
})

const salvar = async () => {
  await formRef.value.validate()
  if (noForm.value.bloqueia) {
    message.warning(`Já existe a unidade "${noForm.value.mesmoNome!.nome}": use outro nome.`)
    return
  }
  await Mon.salvarUnidade(form.dados)
  message.success('Unidade salva')
  form.visivel = false
  await carregar()
}

/** "3 monitores, 1 telefone": o que está ligado à unidade. */
const ligados = (u: Mon.Unidade) =>
  [
    [u.monitores, 'monitor', 'monitores'],
    [u.manutencoes, 'manutenção', 'manutenções'],
    [u.telefones, 'telefone', 'telefones']
  ]
    .filter(([n]) => Number(n) > 0)
    .map(([n, um, varios]) => `${n} ${Number(n) === 1 ? um : varios}`)
    .join(', ')

const excluir = async (u: Mon.Unidade) => {
  const lig = ligados(u)
  await message.delConfirm(
    lig
      ? `Excluir a unidade "${u.nome}"? Ficam sem unidade: ${lig}. Telefones de emergência deixam de ser de emergência.` +
          (parecidas.value.has(u.id!)
            ? ' Se ela é repetida, use Juntar para não perder esses vínculos.'
            : '')
      : `Excluir a unidade "${u.nome}"?`
  )
  await Mon.excluirUnidade(u.id!)
  message.success('Unidade excluída')
  await carregar()
}

// ---------------------------------------------------------------- juntar repetidas
const juntar = reactive({
  visivel: false,
  origem: {} as Mon.Unidade,
  destinoId: undefined as number | undefined,
  salvando: false
})
const destino = computed(() => lista.value.find((u) => u.id === juntar.destinoId))
/** As parecidas primeiro, depois as outras em ordem de nome. */
const opcoesDestino = computed(() => {
  const sugeridas = parecidas.value.get(juntar.origem.id!) || []
  const ids = new Set(sugeridas.map((p) => p.unidade.id))
  return [
    ...sugeridas.map((p) => ({ unidade: p.unidade, dica: p.motivos.join(', ') })),
    ...lista.value
      .filter((u) => u.id !== juntar.origem.id && !ids.has(u.id))
      .map((u) => ({ unidade: u, dica: '' }))
  ]
})
const abrirJuntar = (u: Mon.Unidade) => {
  juntar.origem = u
  juntar.destinoId = parecidas.value.get(u.id!)?.[0]?.unidade.id
  juntar.visivel = true
}
/** Troca qual fica e qual sai. */
const inverterJuntar = () => {
  const d = destino.value
  if (!d) return
  juntar.destinoId = juntar.origem.id
  juntar.origem = d
}
const confirmarJuntar = async () => {
  if (!destino.value) return
  juntar.salvando = true
  try {
    const r = await Mon.juntarUnidades(juntar.origem.id!, destino.value.id!)
    const movidos = ligados({ nome: '', ...r })
    message.success(
      `"${juntar.origem.nome}" foi juntada em "${destino.value.nome}"` +
        (movidos ? `; passaram para ela: ${movidos}.` : '.')
    )
    juntar.visivel = false
    await carregar()
  } finally {
    juntar.salvando = false
  }
}

onMounted(carregar)
</script>
