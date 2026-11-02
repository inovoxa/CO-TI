<template>
  <ContentWrap>
    <div class="flex flex-wrap items-center gap-8px">
      <el-button v-hasPermi="['itsm:formulario:update']" plain type="primary" @click="abrir()">
        <Icon class="mr-5px" icon="ep:plus" /> Novo formulário
      </el-button>
      <el-button v-hasPermi="['itsm:formulario:update']" plain @click="importacao.visivel = true">
        <Icon class="mr-5px" icon="ep:upload" /> Importar
      </el-button>
      <el-input v-model="busca" class="!w-260px" clearable placeholder="Buscar" />
    </div>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="filtrados">
      <el-table-column label="Código" prop="codigo" width="80" />
      <el-table-column label="Formulário" min-width="220" prop="nome" show-overflow-tooltip />
      <el-table-column label="Grupo" min-width="160" prop="grupo" show-overflow-tooltip />
      <el-table-column label="Categoria" min-width="200" prop="categoria" show-overflow-tooltip />
      <el-table-column label="Aprovação" width="150">
        <template #default="{ row }">{{ rotuloAprovacao(row) }}</template>
      </el-table-column>
      <el-table-column label="Automação no AD" width="150">
        <template #default="{ row }">
          <el-tag v-if="row.acaoAd" disable-transitions effect="plain" type="warning">{{
            rotuloAcao(row.acaoAd)
          }}</el-tag>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column align="center" label="Ativo" width="70">
        <template #default="{ row }">{{ row.ativo ? 'Sim' : 'Não' }}</template>
      </el-table-column>
      <el-table-column align="center" label="Ações" width="150">
        <template #default="{ row }">
          <el-button v-hasPermi="['itsm:formulario:update']" link type="primary" @click="abrir(row)"
            >Editar</el-button
          >
          <el-button
            v-hasPermi="['itsm:formulario:update']"
            link
            type="danger"
            @click="excluir(row)"
            >Excluir</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>

  <!-- edição -->
  <Dialog v-model="dialogo" :title="form.id ? 'Editar formulário' : 'Novo formulário'" fullscreen>
    <el-row :gutter="24">
      <el-col :md="14" :span="24">
        <el-form ref="formRef" :model="form" :rules="regras" label-width="130px">
          <el-row :gutter="12">
            <el-col :span="8"
              ><el-form-item label="Código" prop="codigo"
                ><el-input v-model="form.codigo" placeholder="1.2" /></el-form-item
            ></el-col>
            <el-col :span="16"
              ><el-form-item label="Nome" prop="nome"
                ><el-input v-model="form.nome" maxlength="150" /></el-form-item
            ></el-col>
          </el-row>
          <el-form-item label="Grupo do catálogo" prop="grupo">
            <el-autocomplete
              v-model="form.grupo"
              :fetch-suggestions="sugerirGrupos"
              class="w-full"
            />
          </el-form-item>
          <el-form-item label="Descrição"
            ><el-input v-model="form.descricao" maxlength="2000" type="textarea"
          /></el-form-item>
          <el-form-item label="Ícone">
            <el-input v-model="form.icone" placeholder="Iconify, ex.: ep:key" />
          </el-form-item>
          <el-divider content-position="left">Chamado gerado</el-divider>
          <el-form-item label="Título">
            <el-input
              v-model="form.tituloModelo"
              placeholder="Ex.: Reset de senha: {login_usuario_reset}"
            />
          </el-form-item>
          <el-form-item label="Categoria">
            <el-select v-model="form.categoriaId" class="w-full" clearable filterable>
              <el-option
                v-for="c in categorias"
                :key="c.id"
                :label="c.nomeCompleto"
                :value="c.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="Tipo">
            <el-radio-group v-model="form.tipo">
              <el-radio-button :value="1">Incidente</el-radio-button>
              <el-radio-button :value="2">Requisição</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="Urgência">
                <el-select v-model="form.urgencia"
                  ><el-option v-for="e in ESCALA" :key="e.valor" :label="e.rotulo" :value="e.valor"
                /></el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="Impacto">
                <el-select v-model="form.impacto"
                  ><el-option v-for="e in ESCALA" :key="e.valor" :label="e.rotulo" :value="e.valor"
                /></el-select>
              </el-form-item>
            </el-col>
          </el-row>
          <el-divider content-position="left">Aprovação e automação</el-divider>
          <el-form-item label="Quem aprova">
            <el-radio-group v-model="form.aprovacao">
              <el-radio-button value="nenhuma">Ninguém</el-radio-button>
              <el-radio-button value="gestor">Gestor no AD</el-radio-button>
              <el-radio-button value="campo">E-mail informado</el-radio-button>
              <el-radio-button value="fixo">Pessoa fixa</el-radio-button>
            </el-radio-group>
            <div v-if="form.aprovacao !== 'nenhuma'" class="dica">
              Em qualquer opção, só aprova quem tem o papel Aprovador (Sistema → Usuários → Atribuir
              papel).
            </div>
          </el-form-item>
          <el-form-item v-if="form.aprovacao === 'campo'" label="Pergunta do e-mail">
            <el-select v-model="form.aprovadorCampo" class="w-full">
              <el-option
                v-for="p in perguntasEmail"
                :key="p.name"
                :label="`${p.label} (${p.name})`"
                :value="p.name"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.aprovacao === 'fixo'" label="Aprovador">
            <UsuarioSelect v-model="form.aprovadorId" aprovadores class="w-full" filterable />
          </el-form-item>
          <el-form-item label="Operação no AD">
            <el-select
              v-model="form.acaoAd"
              class="w-full"
              clearable
              placeholder="Nenhuma (só abre o chamado)"
            >
              <el-option v-for="a in ACOES_AD" :key="a.valor" :label="a.rotulo" :value="a.valor" />
            </el-select>
            <div v-if="form.acaoAd" class="dica">
              Roda o Executar_AD_Automatico.ps1 depois da aprovação. As perguntas precisam ter os
              nomes que o script lê (ex.: <code>login_usuario_reset</code>,
              <code>nome_completo</code>, <code>perfil_referencia</code>).
            </div>
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="12"
              ><el-form-item label="Ordem"
                ><el-input-number v-model="form.ordem" :min="0" /></el-form-item
            ></el-col>
            <el-col :span="12"
              ><el-form-item label="Ativo"><el-switch v-model="form.ativo" /></el-form-item
            ></el-col>
          </el-row>

          <el-divider content-position="left">Perguntas</el-divider>
          <div v-for="(s, si) in form.definicao.secoes" :key="si" class="secao">
            <div class="mb-8px flex items-center gap-8px">
              <el-input v-model="s.nome" placeholder="Nome da seção" />
              <el-button
                :disabled="form.definicao.secoes.length === 1"
                link
                type="danger"
                @click="form.definicao.secoes.splice(si, 1)"
              >
                Remover seção
              </el-button>
            </div>
            <div v-for="(p, pi) in s.perguntas" :key="pi" class="pergunta">
              <el-input v-model="p.label" class="flex-[2]" placeholder="Texto da pergunta" />
              <el-input v-model="p.name" class="flex-1" placeholder="nome_tecnico" />
              <el-select v-model="p.type" class="!w-130px">
                <el-option v-for="t in TIPOS" :key="t.valor" :label="t.rotulo" :value="t.valor" />
              </el-select>
              <el-checkbox v-model="p.required">obrigatória</el-checkbox>
              <el-button link @click="mover(s.perguntas, pi, -1)"
                ><Icon icon="ep:arrow-up"
              /></el-button>
              <el-button link @click="mover(s.perguntas, pi, 1)"
                ><Icon icon="ep:arrow-down"
              /></el-button>
              <el-button link type="danger" @click="s.perguntas.splice(pi, 1)"
                ><Icon icon="ep:delete"
              /></el-button>
              <el-input
                v-if="p.type === 'select' || p.type === 'radios'"
                :model-value="(p.values || []).join('\n')"
                class="w-full"
                placeholder="Uma opção por linha"
                type="textarea"
                @update:model-value="
                  (v: string) =>
                    (p.values = v
                      .split('\n')
                      .map((x) => x.trim())
                      .filter(Boolean))
                "
              />
              <el-input
                v-if="p.type === 'regex'"
                v-model="p.regex"
                class="w-full"
                placeholder="/^[0-9]{11}$/"
              />
            </div>
            <el-button link type="primary" @click="s.perguntas.push(novaPergunta())"
              ><Icon icon="ep:plus" /> Pergunta</el-button
            >
          </div>
          <el-button
            plain
            @click="form.definicao.secoes.push({ nome: '', perguntas: [novaPergunta()] })"
          >
            <Icon class="mr-4px" icon="ep:plus" /> Seção
          </el-button>
        </el-form>
      </el-col>
      <el-col :md="10" :span="24">
        <div class="previa">
          <div class="mb-10px font-bold">Pré-visualização</div>
          <FormularioRender v-model="previa" :definicao="definicaoValida" />
        </div>
      </el-col>
    </el-row>
    <template #footer>
      <el-button :loading="salvando" type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="dialogo = false">Cancelar</el-button>
    </template>
  </Dialog>

  <!-- importação -->
  <Dialog v-model="importacao.visivel" title="Importar formulários" width="520px">
    <p class="mt-0 text-13px">
      Arquivo no formato <code>formularios_definicoes.json</code> (o mesmo do catálogo do GLPI usado
      antes), com as extensões do CO-TI (<code>acao_ad</code>, <code>aprovacao</code>,
      <code>titulo_modelo</code>…). Formulários com o mesmo código são atualizados.
    </p>
    <el-upload
      :auto-upload="false"
      :limit="1"
      :on-change="(f: any) => (importacao.arquivo = f.raw)"
      accept=".json"
      drag
    >
      <Icon :size="28" icon="ep:upload-filled" />
      <div>Arraste o arquivo ou clique</div>
    </el-upload>
    <el-checkbox v-model="importacao.criarCategorias" class="mt-10px"
      >Criar as categorias que não existirem (grupo › formulário)</el-checkbox
    >
    <el-alert
      v-if="importacao.resultado"
      :closable="false"
      class="mt-10px"
      :type="importacao.resultado.avisos.length ? 'warning' : 'success'"
    >
      <template #title
        >{{ importacao.resultado.criados }} criados,
        {{ importacao.resultado.atualizados }} atualizados</template
      >
      <div v-for="(a, i) in importacao.resultado.avisos" :key="i" class="text-12px">{{ a }}</div>
    </el-alert>
    <template #footer>
      <el-button
        :disabled="!importacao.arquivo"
        :loading="importacao.enviando"
        type="primary"
        @click="importar"
        >Importar</el-button
      >
      <el-button @click="importacao.visivel = false">Fechar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import UsuarioSelect from '@/components/UsuarioSelect/index.vue'
import * as ItsmApi from '@/api/itsm'
import { ACOES_AD, ESCALA } from '@/api/itsm'
import FormularioRender from '../components/FormularioRender.vue'

defineOptions({ name: 'ItsmFormulario' })
const message = useMessage()

const TIPOS = [
  { valor: 'text', rotulo: 'Texto' },
  { valor: 'textarea', rotulo: 'Texto longo' },
  { valor: 'email', rotulo: 'E-mail' },
  { valor: 'number', rotulo: 'Número' },
  { valor: 'date', rotulo: 'Data' },
  { valor: 'select', rotulo: 'Lista' },
  { valor: 'radios', rotulo: 'Opções' },
  { valor: 'regex', rotulo: 'Formato (regex)' },
  { valor: 'location', rotulo: 'Local' },
  { valor: 'usuario', rotulo: 'Usuário de rede (lista de pessoas)' }
]

const loading = ref(true)
const lista = ref<ItsmApi.Formulario[]>([])
const busca = ref('')
const categorias = ref<ItsmApi.Categoria[]>([])
const dialogo = ref(false)
const salvando = ref(false)
const formRef = ref()
const previa = ref<Record<string, string>>({})

const novaPergunta = (): ItsmApi.Pergunta => ({
  name: '',
  label: '',
  type: 'text',
  required: false
})
const vazio = (): ItsmApi.Formulario => ({
  nome: '',
  grupo: '',
  tipo: 2,
  urgencia: 3,
  impacto: 3,
  aprovacao: 'nenhuma',
  ativo: true,
  ordem: 0,
  definicao: { secoes: [{ nome: '', perguntas: [novaPergunta()] }] }
})
const form = ref<ItsmApi.Formulario>(vazio())
const regras = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  grupo: [{ required: true, message: 'Informe o grupo', trigger: 'blur' }]
}

const filtrados = computed(() => {
  const t = busca.value.toLowerCase()
  return t
    ? lista.value.filter((f) => `${f.codigo} ${f.nome} ${f.grupo}`.toLowerCase().includes(t))
    : lista.value
})
const perguntasEmail = computed(() =>
  form.value.definicao.secoes
    .flatMap((s) => s.perguntas)
    .filter((p) => p.name && (p.type === 'email' || p.type === 'regex' || p.type === 'text'))
)
/** Pré-visualização só com perguntas já nomeadas (evita chaves vazias no formulário). */
const definicaoValida = computed<ItsmApi.Definicao>(() => ({
  secoes: form.value.definicao.secoes.map((s) => ({
    nome: s.nome,
    perguntas: s.perguntas.filter((p) => p.name && p.label)
  }))
}))

const rotuloAcao = (a: string) => ACOES_AD.find((x) => x.valor === a)?.rotulo || a
const rotuloAprovacao = (f: ItsmApi.Formulario) =>
  ({
    nenhuma: '—',
    gestor: 'Gestor (AD)',
    campo: `E-mail em ${f.aprovadorCampo}`,
    fixo: f.aprovador || 'Pessoa fixa'
  })[f.aprovacao]

const sugerirGrupos = (q: string, cb: (r: { value: string }[]) => void) =>
  cb(
    [...new Set(lista.value.map((f) => f.grupo))]
      .filter((g) => g.toLowerCase().includes((q || '').toLowerCase()))
      .map((value) => ({ value }))
  )

const mover = (l: ItsmApi.Pergunta[], i: number, d: number) => {
  const j = i + d
  if (j < 0 || j >= l.length) return
  ;[l[i], l[j]] = [l[j], l[i]]
}

const carregar = async () => {
  loading.value = true
  try {
    lista.value = await ItsmApi.getFormularios()
  } finally {
    loading.value = false
  }
}

const abrir = async (f?: ItsmApi.Formulario) => {
  form.value = f ? JSON.parse(JSON.stringify(f)) : vazio()
  previa.value = {}
  dialogo.value = true
  if (!categorias.value.length) {
    categorias.value = await ItsmApi.getCategorias(true)
  }
}

const salvar = async () => {
  if (!(await formRef.value.validate().catch(() => false))) return
  salvando.value = true
  try {
    await ItsmApi.salvarFormulario(form.value)
    message.success('Formulário salvo')
    dialogo.value = false
    await carregar()
  } finally {
    salvando.value = false
  }
}

const excluir = async (f: ItsmApi.Formulario) => {
  await message.delConfirm(
    `Excluir o formulário "${f.nome}"? (com pedidos, desative em vez de excluir)`
  )
  await ItsmApi.excluirFormulario(f.id!)
  await carregar()
}

const importacao = reactive({
  visivel: false,
  arquivo: undefined as File | undefined,
  criarCategorias: true,
  enviando: false,
  resultado: undefined as { criados: number; atualizados: number; avisos: string[] } | undefined
})
const importar = async () => {
  importacao.enviando = true
  try {
    importacao.resultado = (
      await ItsmApi.importarFormularios(importacao.arquivo!, importacao.criarCategorias)
    ).data
    await carregar()
  } finally {
    importacao.enviando = false
  }
}

onMounted(carregar)
</script>

<style scoped>
.secao {
  padding: 10px;
  margin-bottom: 10px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
}
.pergunta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}
.previa {
  position: sticky;
  top: 0;
  padding: 14px;
  background: var(--el-fill-color-lighter);
  border-radius: 8px;
}
.dica {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
</style>
