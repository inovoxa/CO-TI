<template>
  <ContentWrap>
    <div class="flex flex-wrap items-center gap-8px">
      <el-button v-hasPermi="['itsm:regra:update']" plain type="primary" @click="abrir()">
        <Icon class="mr-5px" icon="ep:plus" /> Nova regra
      </el-button>
      <el-button plain @click="teste.visivel = true"
        ><Icon class="mr-5px" icon="ep:video-play" /> Testar regras</el-button
      >
      <span class="text-13px text-[var(--el-text-color-secondary)]">
        Avaliadas na abertura do chamado, na ordem abaixo. Uma regra com “parar” impede as
        seguintes.
      </span>
    </div>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="lista">
      <el-table-column label="Ordem" prop="ordem" width="70" />
      <el-table-column label="Regra" min-width="200" prop="nome" />
      <el-table-column label="Quando" min-width="300">
        <template #default="{ row }">
          <span v-for="(c, i) in row.criterios" :key="i">
            <b v-if="i" class="mx-4px">{{ row.combinacao === 'OU' ? 'ou' : 'e' }}</b>
            {{ rotulo(CAMPOS, c.campo) }} {{ rotulo(OPERADORES, c.operador) }} “{{ c.valor }}”
          </span>
        </template>
      </el-table-column>
      <el-table-column label="Então" min-width="260">
        <template #default="{ row }">
          <div v-for="(a, i) in row.acoes" :key="i"
            >{{ rotulo(ACOES, a.acao)
            }}<template v-if="a.valor">: {{ valorAcao(a) }}</template></div
          >
        </template>
      </el-table-column>
      <el-table-column align="center" label="Parar" width="70">
        <template #default="{ row }">{{ row.parar ? 'Sim' : '—' }}</template>
      </el-table-column>
      <el-table-column align="center" label="Ativa" width="70">
        <template #default="{ row }">{{ row.ativo ? 'Sim' : 'Não' }}</template>
      </el-table-column>
      <el-table-column align="center" label="Ações" width="140">
        <template #default="{ row }">
          <el-button v-hasPermi="['itsm:regra:update']" link type="primary" @click="abrir(row)"
            >Editar</el-button
          >
          <el-button v-hasPermi="['itsm:regra:update']" link type="danger" @click="excluir(row)"
            >Excluir</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <Dialog v-model="dialogo" :title="form.id ? 'Editar regra' : 'Nova regra'" width="860px">
    <el-form ref="formRef" :model="form" :rules="regrasForm" label-width="110px">
      <el-form-item label="Nome" prop="nome"
        ><el-input v-model="form.nome" maxlength="100"
      /></el-form-item>
      <el-form-item label="Descrição"
        ><el-input v-model="form.descricao" maxlength="500"
      /></el-form-item>
      <el-row>
        <el-col :span="8"
          ><el-form-item label="Ordem"
            ><el-input-number v-model="form.ordem" :min="0" /></el-form-item
        ></el-col>
        <el-col :span="8"
          ><el-form-item label="Ativa"><el-switch v-model="form.ativo" /></el-form-item
        ></el-col>
        <el-col :span="8">
          <el-form-item label="Parar">
            <el-switch v-model="form.parar" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="Quando">
        <div class="w-full">
          <el-radio-group v-model="form.combinacao" class="mb-8px" size="small">
            <el-radio-button value="E">todos os critérios</el-radio-button>
            <el-radio-button value="OU">qualquer critério</el-radio-button>
          </el-radio-group>
          <div v-for="(c, i) in form.criterios" :key="i" class="linha">
            <el-select v-model="c.campo" class="!w-180px" placeholder="Campo">
              <el-option v-for="(r, k) in CAMPOS" :key="k" :label="r" :value="k" />
            </el-select>
            <el-select v-model="c.operador" class="!w-150px" placeholder="Operador">
              <el-option v-for="(r, k) in OPERADORES" :key="k" :label="r" :value="k" />
            </el-select>
            <el-select
              v-if="c.campo === 'categoria_id'"
              v-model="c.valor"
              class="flex-1"
              filterable
            >
              <el-option
                v-for="x in categorias"
                :key="x.id"
                :label="x.nomeCompleto"
                :value="String(x.id)"
              />
            </el-select>
            <el-select
              v-else-if="c.campo === 'formulario_id'"
              v-model="c.valor"
              class="flex-1"
              filterable
            >
              <el-option
                v-for="x in formularios"
                :key="x.id"
                :label="`${x.codigo || ''} ${x.nome}`"
                :value="String(x.id)"
              />
            </el-select>
            <el-input v-else v-model="c.valor" class="flex-1" :placeholder="dicaValor(c)" />
            <el-button link type="danger" @click="form.criterios.splice(i, 1)"
              ><Icon icon="ep:delete"
            /></el-button>
          </div>
          <el-button
            link
            type="primary"
            @click="form.criterios.push({ campo: 'titulo', operador: 'contem', valor: '' })"
          >
            <Icon icon="ep:plus" /> Critério
          </el-button>
        </div>
      </el-form-item>
      <el-form-item label="Então">
        <div class="w-full">
          <div v-for="(a, i) in form.acoes" :key="i" class="linha">
            <el-select v-model="a.acao" class="!w-240px" placeholder="Ação" @change="a.valor = ''">
              <el-option v-for="(r, k) in ACOES" :key="k" :label="r" :value="k" />
            </el-select>
            <el-select
              v-if="a.acao === 'atribuir_equipe'"
              v-model="a.valor"
              class="flex-1"
              filterable
            >
              <el-option v-for="x in equipes" :key="x.id" :label="x.nome" :value="String(x.id)" />
            </el-select>
            <UsuarioSelect
              v-else-if="PESSOA.includes(a.acao)"
              :model-value="a.valor ? Number(a.valor) : undefined"
              class="flex-1"
              @update:model-value="(v) => (a.valor = v == null ? '' : String(v))"
            />
            <el-select
              v-else-if="a.acao === 'definir_categoria'"
              v-model="a.valor"
              class="flex-1"
              filterable
            >
              <el-option
                v-for="x in categorias"
                :key="x.id"
                :label="x.nomeCompleto"
                :value="String(x.id)"
              />
            </el-select>
            <el-select v-else-if="a.acao === 'definir_tipo'" v-model="a.valor" class="flex-1">
              <el-option label="Incidente" value="1" />
              <el-option label="Requisição" value="2" />
            </el-select>
            <el-select v-else-if="a.acao.startsWith('definir_')" v-model="a.valor" class="flex-1">
              <el-option
                v-for="e in PRIORIDADES"
                :key="e.valor"
                :label="e.rotulo"
                :value="String(e.valor)"
              />
            </el-select>
            <span v-else class="flex-1 text-12px text-[var(--el-text-color-secondary)]"
              >sem valor</span
            >
            <el-button link type="danger" @click="form.acoes.splice(i, 1)"
              ><Icon icon="ep:delete"
            /></el-button>
          </div>
          <el-button
            link
            type="primary"
            @click="form.acoes.push({ acao: 'atribuir_equipe', valor: '' })"
          >
            <Icon icon="ep:plus" /> Ação
          </el-button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :loading="salvando" type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="dialogo = false">Cancelar</el-button>
    </template>
  </Dialog>

  <Dialog v-model="teste.visivel" title="Testar regras" width="560px">
    <el-form label-width="110px">
      <el-form-item label="Título"><el-input v-model="teste.fatos.titulo" /></el-form-item>
      <el-form-item label="Descrição"
        ><el-input v-model="teste.fatos.descricao" type="textarea"
      /></el-form-item>
      <el-form-item label="Tipo">
        <el-radio-group v-model="teste.fatos.tipo">
          <el-radio-button :value="1">Incidente</el-radio-button>
          <el-radio-button :value="2">Requisição</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="Urgência">
        <el-select v-model="teste.fatos.urgencia"
          ><el-option v-for="e in ESCALA" :key="e.valor" :label="e.rotulo" :value="e.valor"
        /></el-select>
      </el-form-item>
      <el-form-item label="Categoria">
        <el-select v-model="teste.fatos.categoria_id" class="w-full" clearable filterable>
          <el-option v-for="x in categorias" :key="x.id" :label="x.nomeCompleto" :value="x.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <el-alert
      v-if="teste.resultado"
      :closable="false"
      :type="teste.resultado.length ? 'success' : 'info'"
    >
      <template #title>{{
        teste.resultado.length ? 'Ações que seriam aplicadas:' : 'Nenhuma regra casou'
      }}</template>
      <div v-for="(d, i) in teste.resultado" :key="i">
        {{ d.regraNome }} → {{ rotulo(ACOES, d.acao.acao)
        }}<template v-if="d.acao.valor">: {{ valorAcao(d.acao) }}</template>
      </div>
    </el-alert>
    <template #footer>
      <el-button type="primary" @click="testar">Testar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as ItsmApi from '@/api/itsm'
import { ESCALA, PRIORIDADES } from '@/api/itsm'
import UsuarioSelect from '@/components/UsuarioSelect/index.vue'
import { garantirUsuarios, nomeUsuario } from '@/hooks/web/useUsuarios'

defineOptions({ name: 'ItsmRegra' })
const message = useMessage()

const CAMPOS: Record<string, string> = {
  titulo: 'Título',
  descricao: 'Descrição',
  categoria_id: 'Categoria',
  tipo: 'Tipo (1 incidente, 2 requisição)',
  urgencia: 'Urgência (1–5)',
  impacto: 'Impacto (1–5)',
  prioridade: 'Prioridade (1–6)',
  origem_id: 'Origem (nº)',
  dept_id: 'Unidade (nº)',
  solicitante_id: 'Solicitante (nº)',
  formulario_id: 'Formulário'
}
const OPERADORES: Record<string, string> = {
  igual: 'é',
  diferente: 'não é',
  contem: 'contém',
  nao_contem: 'não contém',
  comeca: 'começa com',
  regex: 'casa com a expressão',
  em: 'é um de (a,b,c)',
  maior: 'maior que',
  menor: 'menor que'
}
const ACOES: Record<string, string> = {
  atribuir_equipe: 'Atribuir à equipe',
  atribuir_tecnico: 'Atribuir ao técnico',
  adicionar_observador: 'Incluir observador',
  definir_categoria: 'Definir categoria',
  definir_urgencia: 'Definir urgência',
  definir_impacto: 'Definir impacto',
  definir_prioridade: 'Definir prioridade',
  definir_tipo: 'Definir tipo',
  pedir_aprovacao_gestor: 'Pedir aprovação do gestor (AD)',
  pedir_aprovacao_usuario: 'Pedir aprovação de'
}
const PESSOA = ['atribuir_tecnico', 'adicionar_observador', 'pedir_aprovacao_usuario']
const rotulo = (m: Record<string, string>, k: string) => m[k] || k

const loading = ref(true)
const lista = ref<ItsmApi.Regra[]>([])
const categorias = ref<ItsmApi.Categoria[]>([])
const equipes = ref<ItsmApi.Equipe[]>([])
const formularios = ref<ItsmApi.Formulario[]>([])
const dialogo = ref(false)
const salvando = ref(false)
const formRef = ref()
const vazio = (): ItsmApi.Regra => ({
  nome: '',
  ordem: 0,
  ativo: true,
  combinacao: 'E',
  parar: false,
  criterios: [{ campo: 'titulo', operador: 'contem', valor: '' }],
  acoes: [{ acao: 'atribuir_equipe', valor: '' }]
})
const form = ref<ItsmApi.Regra>(vazio())
const regrasForm = { nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }] }

const dicaValor = (c: ItsmApi.Criterio) =>
  c.operador === 'em' ? 'a,b,c' : c.operador === 'regex' ? 'expressão regular' : 'valor'

const valorAcao = (a: ItsmApi.AcaoRegra) => {
  const n = Number(a.valor)
  if (a.acao === 'atribuir_equipe')
    return equipes.value.find((x) => x.id === n)?.nome || '#' + a.valor
  if (PESSOA.includes(a.acao)) return nomeUsuario(n)
  if (a.acao === 'definir_categoria')
    return categorias.value.find((x) => x.id === n)?.nomeCompleto || '#' + a.valor
  if (a.acao === 'definir_tipo') return n === 1 ? 'Incidente' : 'Requisição'
  if (a.acao.startsWith('definir_'))
    return PRIORIDADES.find((x) => x.valor === n)?.rotulo || a.valor
  return a.valor
}

const carregar = async () => {
  loading.value = true
  try {
    lista.value = await ItsmApi.getRegras()
    garantirUsuarios(
      lista.value.flatMap((r) =>
        r.acoes.filter((a) => PESSOA.includes(a.acao)).map((a) => Number(a.valor))
      )
    )
  } finally {
    loading.value = false
  }
}

const abrir = (r?: ItsmApi.Regra) => {
  form.value = r ? JSON.parse(JSON.stringify(r)) : vazio()
  dialogo.value = true
}

const salvar = async () => {
  if (!(await formRef.value.validate().catch(() => false))) return
  salvando.value = true
  try {
    await ItsmApi.salvarRegra(form.value)
    message.success('Regra salva')
    dialogo.value = false
    await carregar()
  } finally {
    salvando.value = false
  }
}

const excluir = async (r: ItsmApi.Regra) => {
  await message.delConfirm(`Excluir a regra "${r.nome}"?`)
  await ItsmApi.excluirRegra(r.id!)
  await carregar()
}

const teste = reactive({
  visivel: false,
  fatos: {
    titulo: '',
    descricao: '',
    tipo: 1,
    urgencia: 3,
    categoria_id: undefined as number | undefined
  },
  resultado: undefined as { regraNome: string; acao: ItsmApi.AcaoRegra }[] | undefined
})
const testar = async () => {
  teste.resultado = await ItsmApi.testarRegras({ ...teste.fatos })
}

onMounted(async () => {
  await carregar()
  const [c, e, f] = await Promise.all([
    ItsmApi.getCategorias(false),
    ItsmApi.getEquipes(false),
    ItsmApi.getFormularios().catch(() => [])
  ])
  categorias.value = c
  equipes.value = e
  formularios.value = f
})
</script>

<style scoped>
.linha {
  display: flex;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}
</style>
