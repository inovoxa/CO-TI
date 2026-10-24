<template>
  <el-row :gutter="12">
    <el-col :lg="12" :span="24">
      <ContentWrap title="Banco do GLPI">
        <el-alert :closable="false" class="mb-12px" type="info">
          Use um usuário do MySQL/MariaDB só com permissão de leitura (SELECT) no banco do GLPI. A
          senha não é guardada: serve só para esta importação.
        </el-alert>
        <el-form ref="formRef" :model="conexao" :rules="regras" label-width="110px">
          <el-row :gutter="8">
            <el-col :span="16">
              <el-form-item label="Servidor" prop="host">
                <el-input v-model="conexao.host" placeholder="glpi-db.empresa.local" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="Porta" label-width="50px">
                <el-input-number
                  v-model="conexao.porta"
                  :controls="false"
                  :max="65535"
                  :min="1"
                  class="!w-full"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="Banco" prop="banco"
            ><el-input v-model="conexao.banco" placeholder="glpi"
          /></el-form-item>
          <el-form-item label="Usuário" prop="usuario"
            ><el-input v-model="conexao.usuario" autocomplete="off"
          /></el-form-item>
          <el-form-item label="Senha">
            <el-input v-model="conexao.senha" autocomplete="new-password" show-password />
          </el-form-item>
          <el-form-item label="TLS"
            ><el-checkbox v-model="conexao.exigirTls"
              >Exigir conexão cifrada com certificado válido</el-checkbox
            ></el-form-item
          >
        </el-form>
      </ContentWrap>
    </el-col>
    <el-col :lg="12" :span="24">
      <ContentWrap title="O que trazer">
        <el-form label-width="130px">
          <el-form-item label="Itens">
            <div class="flex flex-col">
              <el-checkbox v-model="opcoes.chamados"
                >Chamados (com acompanhamentos, tarefas, soluções e aprovações)</el-checkbox
              >
              <el-checkbox v-model="opcoes.baseConhecimento">Base de conhecimento</el-checkbox>
              <el-checkbox v-model="opcoes.problemasMudancas">Problemas e mudanças</el-checkbox>
              <el-checkbox v-model="opcoes.governanca"
                >Fornecedores, contratos, orçamentos, licenças e projetos</el-checkbox
              >
              <el-checkbox v-model="opcoes.arquivos"
                >Anexos (precisa da pasta files/ do GLPI no servidor)</el-checkbox
              >
            </div>
          </el-form-item>
          <el-form-item label="Pessoas">
            <el-checkbox v-model="opcoes.criarUsuarios"
              >Criar, desativadas, as pessoas que não existem aqui</el-checkbox
            >
          </el-form-item>
          <el-form-item label="Números">
            <el-checkbox v-model="opcoes.manterNumeros"
              >Manter os números dos chamados, problemas e mudanças</el-checkbox
            >
          </el-form-item>
          <el-form-item label="Desde">
            <el-date-picker
              v-model="opcoes.desde"
              placeholder="Tudo"
              type="date"
              value-format="YYYY-MM-DD"
            />
            <el-checkbox v-model="opcoes.incluirLixeira" class="ml-12px"
              >Incluir a lixeira</el-checkbox
            >
          </el-form-item>
          <el-form-item label="Unidade padrão">
            <el-tree-select
              v-model="opcoes.deptPadraoId"
              :data="depts"
              :props="defaultProps"
              check-strictly
              class="w-full"
              clearable
              node-key="id"
              placeholder="A principal"
            />
          </el-form-item>
          <el-form-item>
            <el-button :loading="verificando" @click="verificar"
              ><Icon class="mr-4px" icon="ep:search" />Verificar</el-button
            >
            <el-button
              :disabled="!verificacao || rodando"
              :loading="iniciando"
              type="primary"
              @click="iniciar"
            >
              <Icon class="mr-4px" icon="ep:upload" />Importar
            </el-button>
          </el-form-item>
        </el-form>
      </ContentWrap>
    </el-col>
  </el-row>

  <ContentWrap v-if="verificacao" :title="`O que há no GLPI ${verificacao.versao || ''}`">
    <el-alert
      v-for="a in verificacao.avisos"
      :key="a"
      :closable="false"
      :title="a"
      class="mb-8px"
      show-icon
      type="warning"
    />
    <div class="contagens">
      <div v-for="(n, k) in verificacao.contagens" :key="k" class="contagem">
        <div class="valor">{{ n.toLocaleString('pt-BR') }}</div>
        <div class="rotulo">{{ ROTULOS[k] || k }}</div>
      </div>
    </div>
    <el-descriptions :column="3" border class="mt-12px" size="small">
      <el-descriptions-item label="Pessoas que já existem aqui">{{
        verificacao.usuariosEncontrados
      }}</el-descriptions-item>
      <el-descriptions-item label="Pessoas novas">{{
        verificacao.usuariosNovos
      }}</el-descriptions-item>
      <el-descriptions-item label="Sem login aproveitável">{{
        verificacao.usuariosSemLogin
      }}</el-descriptions-item>
    </el-descriptions>
    <div class="mb-6px mt-12px text-13px font-bold"
      >Entidades do GLPI → unidades daqui (pelo nome)</div
    >
    <el-table :data="verificacao.entidades" max-height="220" size="small">
      <el-table-column label="Entidade" prop="nome" />
      <el-table-column label="Unidade">
        <template #default="{ row }">
          <span v-if="row.dept">{{ row.dept }}</span>
          <span v-else class="text-[var(--el-text-color-placeholder)]">unidade padrão</span>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <ContentWrap title="Importações">
    <el-table
      :data="lista"
      class="cursor-pointer"
      highlight-current-row
      size="small"
      @row-click="(r) => abrir(r.id)"
    >
      <el-table-column label="Nº" prop="id" width="60" />
      <el-table-column label="Origem" min-width="200">
        <template #default="{ row }"
          >{{ row.host }}:{{ row.porta }}/{{ row.banco
          }}<span v-if="row.versaoOrigem"> (GLPI {{ row.versaoOrigem }})</span></template
        >
      </el-table-column>
      <el-table-column label="Situação" width="130">
        <template #default="{ row }">
          <el-tag :type="STATUS[row.status].cor" disable-transitions>{{
            STATUS[row.status].rotulo
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :formatter="dateFormatter" label="Início" prop="iniciadoEm" width="160" />
      <el-table-column :formatter="dateFormatter" label="Fim" prop="terminadoEm" width="160" />
      <el-table-column label="Quem" prop="quem" width="150" />
    </el-table>

    <template v-if="atual">
      <el-divider />
      <div class="mb-8px flex items-center gap-12px">
        <b>Importação nº {{ atual.id }}</b>
        <el-tag :type="STATUS[atual.status].cor" disable-transitions>{{
          STATUS[atual.status].rotulo
        }}</el-tag>
        <span v-if="atual.status === 'executando' && atual.etapa" class="text-13px"
          >agora: {{ ETAPAS[atual.etapa] || atual.etapa }}</span
        >
        <el-button
          v-if="atual.status === 'executando'"
          class="ml-auto"
          plain
          size="small"
          type="danger"
          @click="cancelar"
        >
          Cancelar
        </el-button>
      </div>
      <el-table :data="etapas" size="small">
        <el-table-column label="Etapa" min-width="160">
          <template #default="{ row }">
            <Icon
              v-if="atual.status === 'executando' && row.nome === atual.etapa"
              class="mr-4px is-loading"
              icon="ep:loading"
            />{{ ETAPAS[row.nome] || row.nome }}
          </template>
        </el-table-column>
        <el-table-column align="right" label="Lidos" prop="lidos" width="100" />
        <el-table-column align="right" label="Importados" prop="importados" width="110" />
        <el-table-column align="right" label="Já existiam" prop="existentes" width="110" />
        <el-table-column align="right" label="Com erro" width="100">
          <template #default="{ row }">
            <span :class="{ 'font-bold text-[var(--el-color-danger)]': row.erros }">{{
              row.erros
            }}</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="mb-4px mt-12px text-13px font-bold">Registro</div>
      <pre class="log">{{ atual.log }}</pre>
    </template>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as Imp from '@/api/itsm/importacao'
import * as DeptApi from '@/api/system/dept'
import { defaultProps, handleTree } from '@/utils/tree'
import { dateFormatter } from '@/utils/formatTime'

defineOptions({ name: 'ItsmImportacao' })
const message = useMessage()

const ROTULOS: Record<string, string> = {
  usuarios: 'pessoas',
  equipes: 'grupos técnicos',
  categorias: 'categorias',
  chamados: 'chamados',
  acompanhamentos: 'acompanhamentos',
  tarefas: 'tarefas',
  artigos: 'artigos da base',
  problemas: 'problemas',
  mudancas: 'mudanças',
  fornecedores: 'fornecedores',
  contratos: 'contratos',
  licencas: 'licenças',
  projetos: 'projetos',
  documentos: 'anexos'
}
const ETAPAS: Record<string, string> = {
  entidades: 'Entidades → unidades',
  usuarios: 'Pessoas',
  equipes: 'Grupos → equipes',
  categorias: 'Categorias',
  origens: 'Origens',
  chamados: 'Chamados',
  base_conhecimento: 'Base de conhecimento',
  problemas: 'Problemas',
  mudancas: 'Mudanças',
  vinculos: 'Vínculos entre itens',
  fornecedores: 'Fornecedores',
  orcamentos: 'Orçamentos',
  contratos: 'Contratos',
  licencas: 'Licenças',
  projetos: 'Projetos'
}
const STATUS: Record<string, { rotulo: string; cor: any }> = {
  pendente: { rotulo: 'Pendente', cor: 'info' },
  executando: { rotulo: 'Em andamento', cor: 'primary' },
  concluida: { rotulo: 'Concluída', cor: 'success' },
  erro: { rotulo: 'Com erro', cor: 'danger' },
  cancelada: { rotulo: 'Cancelada', cor: 'warning' }
}
const regras = {
  host: [{ required: true, message: 'Informe o servidor', trigger: 'blur' }],
  banco: [{ required: true, message: 'Informe o banco', trigger: 'blur' }],
  usuario: [{ required: true, message: 'Informe o usuário', trigger: 'blur' }]
}

const formRef = ref()
const conexao = reactive<Imp.Conexao>({
  host: '',
  porta: 3306,
  banco: 'glpi',
  usuario: '',
  senha: '',
  exigirTls: false
})
const opcoes = reactive<Imp.Opcoes>({
  criarUsuarios: true,
  chamados: true,
  baseConhecimento: true,
  problemasMudancas: true,
  governanca: true,
  manterNumeros: true,
  incluirLixeira: false,
  arquivos: true
})
const depts = ref<any[]>([])
const verificacao = ref<Imp.Verificacao>()
const verificando = ref(false)
const iniciando = ref(false)
const lista = ref<Imp.Importacao[]>([])
const atual = ref<Imp.Importacao>()
const rodando = computed(() => lista.value.some((i) => i.status === 'executando'))
const etapas = computed(() =>
  Object.entries(atual.value?.progresso || {}).map(([nome, e]) => ({ nome, ...e }))
)
let relogio: ReturnType<typeof setInterval> | undefined

const verificar = async () => {
  await formRef.value.validate()
  verificando.value = true
  try {
    verificacao.value = await Imp.verificarImportacao({ conexao, opcoes })
  } finally {
    verificando.value = false
  }
}

const iniciar = async () => {
  await message.confirm(
    'Começar a importação? Ela roda em segundo plano e pode ser repetida depois: o que já veio é pulado.'
  )
  iniciando.value = true
  try {
    const id = await Imp.iniciarImportacao({ conexao, opcoes })
    conexao.senha = ''
    await carregar()
    await abrir(id)
  } finally {
    iniciando.value = false
  }
}

const carregar = async () => {
  lista.value = await Imp.getImportacoes()
}

const abrir = async (id: number) => {
  atual.value = await Imp.getImportacao(id)
}

const cancelar = async () => {
  await message.confirm('Cancelar? O que já veio fica.')
  await Imp.cancelarImportacao(atual.value!.id)
}

// acompanha enquanto houver importação rodando
const acompanhar = async () => {
  if (!rodando.value && atual.value?.status !== 'executando') return
  await carregar()
  if (atual.value) await abrir(atual.value.id)
}

onMounted(async () => {
  depts.value = handleTree(await DeptApi.getSimpleDeptList())
  await carregar()
  if (lista.value.length) await abrir(lista.value[0].id)
  relogio = setInterval(acompanhar, 2000)
})
onBeforeUnmount(() => clearInterval(relogio))
</script>

<style scoped>
.contagens {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 10px;
}
.contagem {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.contagem .valor {
  font-size: 20px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.contagem .rotulo {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.log {
  overflow: auto;
  max-height: 320px;
  padding: 10px;
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}
</style>
