<template>
  <ContentWrap>
    <div class="mb-12px flex flex-wrap items-center gap-8px">
      <el-check-tag :checked="!q.alerta" @change="alertar('')"
        >Todos ({{ contagens.todos ?? 0 }})</el-check-tag
      >
      <el-check-tag :checked="q.alerta === 'sem_suporte'" @change="alertar('sem_suporte')">
        Sistema sem suporte ({{ contagens.sem_suporte ?? 0 }})
      </el-check-tag>
      <el-check-tag :checked="q.alerta === 'antivirus'" @change="alertar('antivirus')">
        Antivírus com problema ({{ contagens.antivirus ?? 0 }})
      </el-check-tag>
      <el-check-tag :checked="q.alerta === 'sem_contato'" @change="alertar('sem_contato')">
        Sem comunicação ({{ contagens.sem_contato ?? 0 }})
      </el-check-tag>
    </div>
    <el-form :inline="true" class="-mb-15px" label-width="0">
      <el-form-item>
        <el-input
          v-model="q.busca"
          class="!w-280px"
          clearable
          placeholder="Nome, patrimônio, série, IP ou usuário"
          @keyup.enter="buscar"
        >
          <template #prefix><Icon icon="ep:search" /></template>
        </el-input>
      </el-form-item>
      <el-form-item>
        <el-select v-model="q.tipo" class="!w-170px" clearable placeholder="Tipo" @change="buscar">
          <el-option v-for="t in TIPOS_ATIVO" :key="t.valor" :label="t.rotulo" :value="t.valor" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-select
          v-model="q.situacao"
          class="!w-150px"
          clearable
          placeholder="Situação"
          @change="buscar"
        >
          <el-option
            v-for="s in SITUACOES_ATIVO"
            :key="s.valor"
            :label="s.rotulo"
            :value="s.valor"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-tree-select
          v-model="q.deptId"
          :data="depts"
          :props="defaultProps"
          check-strictly
          class="!w-220px"
          clearable
          node-key="id"
          placeholder="Unidade"
          @change="buscar"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="buscar"><Icon class="mr-5px" icon="ep:search" /> Buscar</el-button>
        <el-button link type="primary" @click="maisFiltros = !maisFiltros">
          <Icon :icon="maisFiltros ? 'ep:arrow-up' : 'ep:arrow-down'" class="mr-4px" />
          Mais filtros<template v-if="filtrosExtras"> ({{ filtrosExtras }})</template>
        </el-button>
        <el-button v-if="pode" plain type="primary" @click="abrirNovo">
          <Icon class="mr-5px" icon="ep:plus" /> Cadastrar sem agente
        </el-button>
      </el-form-item>
    </el-form>
    <el-form v-show="maisFiltros" :inline="true" class="mt-10px -mb-15px" label-width="0">
      <el-form-item>
        <el-select
          v-model="q.sistema"
          class="!w-260px"
          clearable
          filterable
          placeholder="Sistema operacional"
          @change="buscar"
        >
          <el-option
            v-for="o in opcoes.sistemas"
            :key="o.valor"
            :label="`${o.valor} (${o.total})`"
            :value="o.valor"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-select
          v-model="q.fabricante"
          class="!w-190px"
          clearable
          filterable
          placeholder="Fabricante"
          @change="buscar"
        >
          <el-option
            v-for="o in opcoes.fabricantes"
            :key="o.valor"
            :label="`${o.valor} (${o.total})`"
            :value="o.valor"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-select
          v-model="q.modelo"
          class="!w-200px"
          clearable
          filterable
          placeholder="Modelo"
          @change="buscar"
        >
          <el-option
            v-for="o in opcoes.modelos"
            :key="o.valor"
            :label="`${o.valor} (${o.total})`"
            :value="o.valor"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-select
          v-model="q.antivirus"
          class="!w-160px"
          clearable
          placeholder="Antivírus"
          @change="buscar"
        >
          <el-option v-for="av in ANTIVIRUS" :key="av.valor" :label="av.rotulo" :value="av.valor" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-select
          v-model="q.origem"
          class="!w-170px"
          clearable
          placeholder="Origem"
          @change="buscar"
        >
          <el-option v-for="(r, v) in ORIGENS" :key="v" :label="r" :value="v" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <span class="mr-6px text-13px">Memória</span>
        <el-input-number
          v-model="q.memoriaMinGb"
          :min="0"
          class="!w-110px"
          controls-position="right"
          placeholder="de (GB)"
          @change="buscar"
        />
        <span class="mx-4px">a</span>
        <el-input-number
          v-model="q.memoriaMaxGb"
          :min="0"
          class="!w-110px"
          controls-position="right"
          placeholder="até (GB)"
          @change="buscar"
        />
      </el-form-item>
      <el-form-item>
        <el-button v-if="filtrosExtras" link @click="limparExtras">Limpar</el-button>
      </el-form-item>
    </el-form>
    <div v-if="q.softwareId" class="mt-10px">
      <el-tag closable disable-transitions @close="limparSoftware"
        >Com o software: {{ route.query.software || '#' + q.softwareId }}</el-tag
      >
    </div>
  </ContentWrap>

  <ContentWrap>
    <el-table
      v-loading="loading"
      :data="lista"
      class="cursor-pointer"
      @row-click="(r) => abrir(r.id)"
    >
      <el-table-column label="Nome" min-width="190">
        <template #default="{ row }">
          <Icon :icon="icone(row.tipo)" class="mr-6px align-middle" />
          <span class="font-medium">{{ row.nome }}</span>
          <div v-if="row.patrimonio" class="text-12px text-[var(--el-text-color-secondary)]">
            Patrimônio {{ row.patrimonio }}
          </div>
        </template>
      </el-table-column>
      <el-table-column label="Sistema" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.soNome || '—' }}
          <el-tag
            v-if="row.soSemSuporte"
            class="ml-4px"
            disable-transitions
            size="small"
            type="danger"
            >sem suporte</el-tag
          >
        </template>
      </el-table-column>
      <el-table-column label="Usuário" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ row.usuarioNome || row.usuarioLogin || '—' }}</template>
      </el-table-column>
      <el-table-column label="Unidade" min-width="150" prop="deptNome" show-overflow-tooltip />
      <el-table-column label="IP" prop="ip" width="130" />
      <el-table-column label="Antivírus" width="125">
        <template #default="{ row }">
          <el-tag
            v-if="AGENTES.includes(row.origem)"
            :type="cor(ANTIVIRUS, row.antivirus)"
            disable-transitions
            size="small"
            >{{ rotulo(ANTIVIRUS, row.antivirus) }}</el-tag
          >
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="Último contato" width="150">
        <template #default="{ row }">
          <span :class="row.semContato ? 'text-[var(--el-color-danger)]' : ''">{{
            quando(row.ultimoContato)
          }}</span>
        </template>
      </el-table-column>
      <el-table-column label="Situação" width="115">
        <template #default="{ row }">
          <el-tag :type="cor(SITUACOES_ATIVO, row.situacao)" disable-transitions size="small">{{
            rotulo(SITUACOES_ATIVO, row.situacao)
          }}</el-tag>
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

  <!-- detalhe -->
  <el-drawer v-model="detalhe.visivel" :size="largura" destroy-on-close>
    <template #header>
      <div v-if="detalhe.d" class="min-w-0">
        <div class="truncate text-17px font-bold">
          <Icon :icon="icone(detalhe.d.ativo.tipo)" class="mr-6px align-middle" />{{
            detalhe.d.ativo.nome
          }}
        </div>
        <div class="mt-6px flex flex-wrap gap-6px">
          <el-tag :type="cor(SITUACOES_ATIVO, detalhe.d.ativo.situacao)" disable-transitions>{{
            rotulo(SITUACOES_ATIVO, detalhe.d.ativo.situacao)
          }}</el-tag>
          <el-tag disable-transitions type="info">{{
            rotulo(TIPOS_ATIVO, detalhe.d.ativo.tipo)
          }}</el-tag>
          <el-tag disable-transitions type="info">{{ ORIGENS[detalhe.d.ativo.origem] }}</el-tag>
          <el-tag v-if="detalhe.d.ativo.soSemSuporte" disable-transitions type="danger"
            >Sistema sem suporte do fabricante</el-tag
          >
          <el-tag v-if="detalhe.d.ativo.semContato" disable-transitions type="danger"
            >Sem comunicação</el-tag
          >
        </div>
      </div>
    </template>
    <template v-if="detalhe.d">
      <div v-if="pode" class="mb-12px">
        <el-button @click="abrirEdicao"><Icon class="mr-4px" icon="ep:edit" />Editar</el-button>
      </div>
      <el-alert
        v-for="al in detalhe.d.alertas"
        :key="al.id"
        :closable="false"
        class="mb-8px"
        show-icon
        type="warning"
      >
        <template #title>
          {{ al.titulo }}
          <span v-if="al.chamadoId"> · chamado #{{ al.chamadoId }}</span>
        </template>
        <span v-if="al.detalhe">{{ al.detalhe }}</span>
      </el-alert>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="Patrimônio">{{ a.patrimonio || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Número de série">{{
          a.numeroSerie || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="Fabricante">{{ a.fabricante || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Modelo">{{ a.modelo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Sistema">
          {{ a.soNome || '—' }}
          <span v-if="a.soVersao" class="text-12px"> ({{ a.soVersao }})</span>
          <span v-if="a.soArquitetura" class="text-12px"> · {{ a.soArquitetura }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="Processador">
          {{ a.cpu || '—' }}<span v-if="a.cpuNucleos"> · {{ a.cpuNucleos }} núcleos</span>
        </el-descriptions-item>
        <el-descriptions-item label="Memória">{{ gb(a.memoriaMb) }}</el-descriptions-item>
        <el-descriptions-item label="Disco">
          {{ gb(a.discoTotalMb) }}
          <span v-if="a.discoLivreMb !== undefined && a.discoLivreMb !== null">
            · {{ gb(a.discoLivreMb) }} livres</span
          >
        </el-descriptions-item>
        <el-descriptions-item label="IP / MAC"
          >{{ a.ip || '—' }}<span v-if="a.mac"> · {{ a.mac }}</span></el-descriptions-item
        >
        <el-descriptions-item label="Domínio">{{ a.dominio || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Usuário">
          {{ a.usuarioNome || '—' }}
          <span v-if="a.usuarioLogin" class="text-12px text-[var(--el-text-color-secondary)]">
            ({{ a.usuarioLogin }})</span
          >
        </el-descriptions-item>
        <el-descriptions-item label="Unidade">
          {{ a.deptNome || '—' }}
          <span v-if="a.deptManual" class="text-12px text-[var(--el-text-color-secondary)]">
            (escolhida à mão)</span
          >
        </el-descriptions-item>
        <el-descriptions-item v-if="AGENTES.includes(a.origem)" label="Antivírus">
          <el-tag :type="cor(ANTIVIRUS, a.antivirus)" disable-transitions size="small">{{
            rotulo(ANTIVIRUS, a.antivirus)
          }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="a.origem !== 'manual'" label="Agente">{{
          a.agente || '—'
        }}</el-descriptions-item>
        <el-descriptions-item v-if="a.origem !== 'manual'" label="Último inventário">{{
          quando(a.ultimoInventario)
        }}</el-descriptions-item>
        <el-descriptions-item v-if="a.origem !== 'manual'" label="Último contato">{{
          quando(a.ultimoContato)
        }}</el-descriptions-item>
        <el-descriptions-item v-if="a.paginas" label="Páginas impressas">{{
          a.paginas.toLocaleString('pt-BR')
        }}</el-descriptions-item>
        <el-descriptions-item v-if="a.localizacao" label="Localização (SNMP)">{{
          a.localizacao
        }}</el-descriptions-item>
        <el-descriptions-item v-if="a.observacao" :span="2" label="Observação">{{
          a.observacao
        }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="aba" class="mt-12px" @tab-change="trocarAba">
        <el-tab-pane :label="`Componentes (${detalhe.d.componentes.length})`" name="componentes">
          <el-empty
            v-if="!detalhe.d.componentes.length"
            :image-size="60"
            description="O agente ainda não mandou as peças"
          />
          <div v-for="(itens, tipo) in componentesPorTipo" :key="tipo" class="mb-12px">
            <div class="mb-4px text-13px font-bold">{{ COMPONENTES[tipo] || tipo }}</div>
            <ul class="m-0 pl-18px text-13px leading-22px">
              <li v-for="c in itens" :key="c.id">{{ c.descricao }}</li>
            </ul>
          </div>
        </el-tab-pane>
        <el-tab-pane :label="`Softwares (${detalhe.d.softwares.length})`" name="softwares">
          <el-input
            v-model="filtroSoftware"
            class="mb-8px !w-260px"
            clearable
            placeholder="Filtrar"
            size="small"
          />
          <el-table :data="softwaresFiltrados" max-height="460" size="small">
            <el-table-column label="Nome" min-width="220" prop="nome" show-overflow-tooltip />
            <el-table-column label="Versão" prop="versao" width="130" show-overflow-tooltip />
            <el-table-column label="Editora" min-width="150" prop="editora" show-overflow-tooltip />
            <el-table-column label="Instalado em" width="110">
              <template #default="{ row }">{{ dataCurta(row.instaladoEm) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`Chamados (${detalhe.d.chamados.length})`" name="chamados">
          <el-empty
            v-if="!detalhe.d.chamados.length"
            :image-size="60"
            description="Nenhum chamado ligado a este ativo"
          />
          <el-table v-else :data="detalhe.d.chamados" size="small">
            <el-table-column label="Nº" width="80">
              <template #default="{ row }">
                <el-link type="primary" @click="irChamado(row.chamadoId)"
                  >#{{ row.chamadoId }}</el-link
                >
              </template>
            </el-table-column>
            <el-table-column label="Título" min-width="240" prop="titulo" show-overflow-tooltip />
            <el-table-column label="Aberto em" width="150">
              <template #default="{ row }">{{ quando(row.abertoEm) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="Histórico" name="historico">
          <el-table v-loading="hist.carregando" :data="hist.lista" size="small">
            <el-table-column label="Quando" width="150">
              <template #default="{ row }">{{ quando(row.quando) }}</template>
            </el-table-column>
            <el-table-column label="O quê" min-width="300">
              <template #default="{ row }">{{ descreverHistorico(row) }}</template>
            </el-table-column>
            <el-table-column label="Quem" prop="usuario" width="140" show-overflow-tooltip />
          </el-table>
          <Pagination
            v-model:limit="hist.pageSize"
            v-model:page="hist.pageNo"
            :total="hist.total"
            @pagination="carregarHistorico"
          />
        </el-tab-pane>
      </el-tabs>
    </template>
  </el-drawer>

  <!-- edição / cadastro -->
  <Dialog
    v-model="form.visivel"
    :title="form.novo ? 'Cadastrar ativo' : 'Editar ativo'"
    width="720px"
  >
    <el-alert
      v-if="!form.novo && !form.manual"
      :closable="false"
      class="mb-12px"
      show-icon
      title="Nome, peças, sistema e usuário vêm do agente de inventário e são atualizados por ele."
      type="info"
    />
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="120px">
      <el-row :gutter="12">
        <template v-if="form.novo || form.manual">
          <el-col :span="12">
            <el-form-item label="Nome" prop="nome"
              ><el-input v-model="form.dados.nome" maxlength="255"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Tipo" prop="tipo">
              <el-select v-model="form.dados.tipo" class="w-full">
                <el-option
                  v-for="t in TIPOS_ATIVO"
                  :key="t.valor"
                  :label="t.rotulo"
                  :value="t.valor"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Fabricante"
              ><el-input v-model="form.dados.fabricante" maxlength="255"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Modelo"
              ><el-input v-model="form.dados.modelo" maxlength="255"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Número de série"
              ><el-input v-model="form.dados.numeroSerie" maxlength="100"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="IP"
              ><el-input v-model="form.dados.ip" maxlength="64"
            /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Usuário">
              <el-select v-model="form.dados.usuarioId" class="w-full" clearable filterable>
                <el-option v-for="u in usuarios" :key="u.id" :label="u.nickname" :value="u.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </template>
        <el-col :span="12">
          <el-form-item label="Patrimônio"
            ><el-input v-model="form.dados.patrimonio" maxlength="100"
          /></el-form-item>
        </el-col>
        <el-col v-if="!form.novo" :span="12">
          <el-form-item label="Situação">
            <el-select v-model="form.dados.situacao" class="w-full">
              <el-option
                v-for="s in SITUACOES_ATIVO"
                :key="s.valor"
                :label="s.rotulo"
                :value="s.valor"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Unidade">
            <el-tree-select
              v-model="form.dados.deptId"
              :data="depts"
              :disabled="form.dados.deptAutomatico"
              :props="defaultProps"
              check-strictly
              class="w-full"
              clearable
              node-key="id"
              placeholder="A do usuário"
            />
            <el-checkbox
              v-if="!form.novo && form.deptManual"
              v-model="form.dados.deptAutomatico"
              class="mt-4px"
              >Voltar a seguir a unidade do usuário</el-checkbox
            >
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="Observação"
            ><el-input v-model="form.dados.observacao" :rows="3" type="textarea"
          /></el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Inv from '@/api/inventario'
import {
  ANTIVIRUS,
  COMPONENTES,
  ORIGENS,
  SITUACOES_ATIVO,
  TIPOS_ATIVO,
  cor,
  gb,
  rotulo
} from '@/api/inventario'
import * as DeptApi from '@/api/system/dept'
import * as UserApi from '@/api/system/user'
import { checkPermi } from '@/utils/permission'
import { defaultProps, handleTree } from '@/utils/tree'

defineOptions({ name: 'InventarioAtivo' })
/** Origens com agente instalado (antivírus e contato só fazem sentido nelas) */
const AGENTES = ['ocs', 'glpi_agent']
const message = useMessage()
const route = useRoute()
const router = useRouter()
const pode = checkPermi(['inventario:ativo:update'])
const largura = computed(() => (window.innerWidth < 1100 ? '100%' : '62%'))
const regras = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  tipo: [{ required: true, message: 'Escolha o tipo', trigger: 'change' }]
}

const loading = ref(true)
const lista = ref<Inv.Ativo[]>([])
const total = ref(0)
const contagens = ref<Record<string, number>>({})
const depts = ref<any[]>([])
const usuarios = ref<UserApi.UserVO[]>([])
const q = reactive({
  pageNo: 1,
  pageSize: 20,
  busca: '',
  tipo: undefined as string | undefined,
  situacao: undefined as string | undefined,
  deptId: undefined as number | undefined,
  alerta: '',
  softwareId: undefined as number | undefined,
  sistema: undefined as string | undefined,
  fabricante: undefined as string | undefined,
  modelo: undefined as string | undefined,
  antivirus: undefined as string | undefined,
  origem: undefined as string | undefined,
  memoriaMinGb: undefined as number | undefined,
  memoriaMaxGb: undefined as number | undefined
})
const maisFiltros = ref(false)
const opcoes = ref<Inv.Opcoes>({ sistemas: [], fabricantes: [], modelos: [] })
const EXTRAS = [
  'sistema',
  'fabricante',
  'modelo',
  'antivirus',
  'origem',
  'memoriaMinGb',
  'memoriaMaxGb'
] as const
const filtrosExtras = computed(
  () => EXTRAS.filter((k) => q[k] !== undefined && q[k] !== null && q[k] !== '').length
)
const limparExtras = () => {
  for (const k of EXTRAS) (q as any)[k] = undefined
  buscar()
}
const detalhe = reactive({ visivel: false, d: undefined as Inv.Detalhe | undefined })
const a = computed(() => detalhe.d!.ativo)
const aba = ref('componentes')
const filtroSoftware = ref('')
const hist = reactive({
  pageNo: 1,
  pageSize: 20,
  total: 0,
  lista: [] as Inv.Historico[],
  carregando: false
})
const formRef = ref()
const form = reactive({
  visivel: false,
  novo: false,
  manual: false,
  deptManual: false,
  dados: {} as any
})

const icone = (tipo: string) => TIPOS_ATIVO.find((t) => t.valor === tipo)?.icone ?? 'ep:box'
const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—'
const dataCurta = (d?: string) => (d ? d.split('-').reverse().join('/') : '—')

const componentesPorTipo = computed(() => {
  const g: Record<string, Inv.Componente[]> = {}
  for (const c of detalhe.d?.componentes ?? []) (g[c.tipo] ||= []).push(c)
  return g
})
const softwaresFiltrados = computed(() => {
  const f = filtroSoftware.value.trim().toLowerCase()
  const l = detalhe.d?.softwares ?? []
  return f ? l.filter((s) => `${s.nome} ${s.editora ?? ''}`.toLowerCase().includes(f)) : l
})

const CAMPOS: Record<string, string> = {
  nome: 'Nome',
  tipo: 'Tipo',
  fabricante: 'Fabricante',
  modelo: 'Modelo',
  numero_serie: 'Número de série',
  patrimonio: 'Patrimônio',
  so_nome: 'Sistema',
  so_versao: 'Versão do sistema',
  so_arquitetura: 'Arquitetura',
  so_sem_suporte: 'Sem suporte',
  cpu: 'Processador',
  cpu_nucleos: 'Núcleos',
  memoria_mb: 'Memória (MB)',
  disco_total_mb: 'Disco (MB)',
  mac: 'MAC',
  ip: 'IP',
  dominio: 'Domínio',
  usuario_login: 'Usuário logado',
  usuario_id: 'Usuário',
  antivirus: 'Antivírus',
  situacao: 'Situação',
  dept_id: 'Unidade',
  device_id: 'Identificador do agente'
}
const descreverHistorico = (h: Inv.Historico) => {
  const de = h.antes ?? '—'
  const para = h.depois ?? '—'
  switch (h.tipo) {
    case 'componente_incluido':
      return `Peça incluída (${COMPONENTES[h.campo ?? ''] ?? h.campo ?? ''}): ${para}`
    case 'componente_removido':
      return `Peça removida (${COMPONENTES[h.campo ?? ''] ?? h.campo ?? ''}): ${para}`
    case 'software_instalado':
      return `Software instalado: ${para}`
    case 'software_removido':
      return `Software removido: ${para}`
    default:
      if (h.campo === 'inventario' || h.campo === 'cadastro' || h.campo === 'chamado')
        return h.depois ?? `Desligado: ${de}`
      return `${CAMPOS[h.campo ?? ''] ?? h.campo}: ${de} → ${para}`
  }
}

const carregar = async () => {
  loading.value = true
  try {
    const params: any = { ...q }
    for (const k of Object.keys(params)) {
      if (params[k] === '' || params[k] === null || params[k] === undefined) delete params[k]
    }
    const [r, c] = await Promise.all([Inv.getAtivos(params), Inv.getContagens()])
    lista.value = r.list
    total.value = r.total
    contagens.value = c
  } finally {
    loading.value = false
  }
}
const buscar = () => {
  q.pageNo = 1
  carregar()
}
const alertar = (v: string) => {
  q.alerta = v
  buscar()
}
const limparSoftware = () => {
  q.softwareId = undefined
  router.replace({ query: {} })
  buscar()
}

const abrir = async (id: number) => {
  detalhe.d = await Inv.getAtivo(id)
  aba.value = 'componentes'
  filtroSoftware.value = ''
  hist.pageNo = 1
  hist.lista = []
  detalhe.visivel = true
}
const trocarAba = (nome: string | number) => {
  if (nome === 'historico' && !hist.lista.length) carregarHistorico()
}
const carregarHistorico = async () => {
  hist.carregando = true
  try {
    const r = await Inv.getHistorico({
      id: a.value.id,
      pageNo: hist.pageNo,
      pageSize: hist.pageSize
    })
    hist.lista = r.list
    hist.total = r.total
  } finally {
    hist.carregando = false
  }
}

const carregarListas = async () => {
  if (!usuarios.value.length) usuarios.value = await UserApi.getSimpleUserList()
}

const abrirNovo = async () => {
  form.novo = true
  form.manual = true
  form.deptManual = false
  form.dados = { nome: '', tipo: 'impressora' }
  form.visivel = true
  await carregarListas()
}
const abrirEdicao = async () => {
  const x = a.value
  form.novo = false
  form.manual = x.origem === 'manual'
  form.deptManual = !!x.deptManual
  form.dados = {
    id: x.id,
    nome: x.nome,
    tipo: x.tipo,
    fabricante: x.fabricante,
    modelo: x.modelo,
    numeroSerie: x.numeroSerie,
    ip: x.ip,
    usuarioId: x.usuarioId,
    patrimonio: x.patrimonio ?? '',
    situacao: x.situacao,
    deptId: x.deptId,
    deptAutomatico: false,
    observacao: x.observacao ?? ''
  }
  form.visivel = true
  if (form.manual) await carregarListas()
}
const salvar = async () => {
  await formRef.value.validate()
  let id: number
  if (form.novo) {
    id = await Inv.criarAtivo(form.dados)
    message.success('Ativo cadastrado')
  } else {
    const d = { ...form.dados }
    if (!form.manual) {
      for (const k of ['nome', 'tipo', 'fabricante', 'modelo', 'numeroSerie', 'ip', 'usuarioId'])
        delete d[k]
    }
    if (d.deptAutomatico || d.deptId === a.value.deptId) delete d.deptId
    await Inv.atualizarAtivo(d)
    id = d.id
    message.success('Ativo atualizado')
  }
  form.visivel = false
  await carregar()
  await abrir(id)
}

const irChamado = (id: number) => router.push({ path: '/servicedesk/chamados', query: { id } })

/** Filtros vindos de outras telas (softwares, agentes); a página fica em cache, então vale também ao voltar */
const aplicarQuery = async () => {
  q.softwareId = route.query.softwareId ? Number(route.query.softwareId) : undefined
  if (route.query.alerta) q.alerta = String(route.query.alerta)
  q.pageNo = 1
  await carregar()
  const id = Number(route.query.id)
  if (id) abrir(id)
}

watch(
  () => route.fullPath,
  () => {
    if (route.name === 'InventarioAtivo' && Object.keys(route.query).length) aplicarQuery()
  }
)

onMounted(async () => {
  depts.value = handleTree(await DeptApi.getSimpleDeptList())
  Inv.getOpcoes().then((o) => (opcoes.value = o))
  await aplicarQuery()
})
</script>
