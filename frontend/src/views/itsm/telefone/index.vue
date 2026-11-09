<template>
  <ContentWrap>
    <div class="flex flex-wrap items-center gap-8px">
      <el-input
        v-model="q.busca"
        class="!w-280px"
        clearable
        placeholder="Buscar por nome, setor ou número"
        @clear="buscar"
        @keyup.enter="buscar"
      >
        <template #prefix><Icon icon="ep:search" /></template>
      </el-input>
      <el-select
        v-model="q.unidadeId"
        class="!w-240px"
        clearable
        filterable
        placeholder="Todas as unidades"
        @change="buscar"
      >
        <el-option v-for="u in unidades" :key="u.id" :label="u.nome" :value="u.id" />
      </el-select>
      <el-select
        v-model="q.setor"
        class="!w-180px"
        clearable
        filterable
        placeholder="Todos os setores"
        @change="buscar"
      >
        <el-option v-for="s in setores" :key="s" :label="s" :value="s" />
      </el-select>
      <el-checkbox v-model="q.emergencia" label="Só emergência" @change="buscar" />
      <el-button @click="buscar">Buscar</el-button>
      <el-button plain @click="exportar">
        <Icon class="mr-4px" icon="ep:download" /> Exportar
      </el-button>
      <div v-hasPermi="['itsm:telefone:update']" class="ml-auto flex gap-8px">
        <el-button plain @click="baixarModelo"> Baixar modelo </el-button>
        <el-upload
          accept=".xlsx"
          :auto-upload="false"
          :on-change="importar"
          :show-file-list="false"
        >
          <el-button :loading="importando" plain> Importar planilha </el-button>
        </el-upload>
        <el-button plain type="primary" @click="abrir()">
          <Icon class="mr-4px" icon="ep:plus" /> Novo contato
        </el-button>
      </div>
    </div>
  </ContentWrap>

  <el-alert
    v-if="unidadeFiltrada?.linkFora"
    class="mb-12px"
    :closable="false"
    show-icon
    :title="`Link de ${unidadeFiltrada.nome} fora: ramal pode não funcionar. Tente o celular ou o WhatsApp.`"
    type="error"
  />

  <ContentWrap>
    <el-empty
      v-if="!loading && !lista.length"
      :image-size="60"
      :description="filtrando ? 'Nenhum contato encontrado' : 'A lista ainda está vazia'"
    />
    <el-table v-else v-loading="loading" :data="lista">
      <el-table-column label="Nome" min-width="190">
        <template #default="{ row }">
          {{ row.nome }}
          <el-tag v-if="row.emergencia" disable-transitions size="small" type="danger">
            Emergência
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Unidade" min-width="190">
        <template #default="{ row }">
          {{ row.unidade }}
          <el-tag v-if="row.linkFora" disable-transitions effect="dark" size="small" type="danger">
            Link fora
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Contato" min-width="290">
        <template #default="{ row }"><ContatoAcoes :t="row" /></template>
      </el-table-column>
      <el-table-column label="Setor" min-width="130" prop="setor" show-overflow-tooltip />
      <el-table-column label="E-mail" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <a v-if="row.email" :href="`mailto:${row.email}`">{{ row.email }}</a>
        </template>
      </el-table-column>
      <el-table-column label="Observação" min-width="150" prop="observacao" show-overflow-tooltip />
      <el-table-column
        v-if="checkPermi(['itsm:telefone:update'])"
        align="right"
        fixed="right"
        label=""
        width="160"
      >
        <template #default="{ row }">
          <el-button link type="primary" @click="abrir(row)">Editar</el-button>
          <el-button link type="danger" @click="excluir(row)">Excluir</el-button>
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

  <el-dialog
    v-model="dialogo"
    :title="form.id ? 'Editar contato' : 'Novo contato'"
    width="min(600px, 94vw)"
  >
    <el-form ref="formRef" :model="form" :rules="regras" label-position="top">
      <el-form-item label="Nome (pessoa, sala ou serviço)" prop="nome">
        <el-input v-model="form.nome" maxlength="120" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="Unidade" prop="unidadeId">
            <el-select v-model="form.unidadeId" class="w-full" clearable filterable>
              <el-option v-for="u in unidades" :key="u.id" :label="u.nome" :value="u.id" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Setor">
            <el-autocomplete
              v-model="form.setor"
              class="w-full"
              :fetch-suggestions="sugerirSetores"
              maxlength="120"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="Ramal" prop="ramal">
            <el-input v-model="form.ramal" maxlength="20" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Telefone fixo" prop="telefone">
            <el-input v-model="form.telefone" maxlength="30" placeholder="(16) 3301-0000" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Celular" prop="celular">
            <el-input v-model="form.celular" maxlength="30" placeholder="(16) 99999-0000" />
          </el-form-item>
        </el-col>
      </el-row>
      <div class="mb-12px flex flex-wrap gap-x-16px gap-y-4px">
        <el-checkbox v-model="form.whatsapp" :disabled="!form.celular?.trim()" label="Tem WhatsApp" />
        <el-checkbox
          v-model="form.celularPublico"
          :disabled="!form.celular?.trim()"
          label="Celular corporativo (todos veem)"
        />
        <el-checkbox v-model="form.emergencia" label="Contato de emergência da unidade" />
      </div>
      <el-form-item label="E-mail" prop="email">
        <el-input v-model="form.email" maxlength="128" />
      </el-form-item>
      <el-form-item label="Observação">
        <el-input v-model="form.observacao" maxlength="255" />
      </el-form-item>
      <p class="nota">
        Ramal e telefone fixo aparecem para todos com acesso à lista. Celular pessoal só aparece para
        a equipe de TI.
      </p>
    </el-form>
    <template #footer>
      <el-button @click="dialogo = false">Cancelar</el-button>
      <el-button :loading="salvando" type="primary" @click="salvar">Salvar</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="relatorioAberto" title="Resultado da importação" width="min(560px, 94vw)">
    <template v-if="relatorio">
      <p>
        {{ relatorio.criados }} criados, {{ relatorio.atualizados }} atualizados e
        {{ relatorio.recusados }} recusados.
      </p>
      <ul v-if="relatorio.erros.length" class="erros">
        <li v-for="e in relatorio.erros" :key="e">{{ e }}</li>
      </ul>
      <p v-if="relatorio.recusados > relatorio.erros.length" class="nota">
        Mostrando os {{ relatorio.erros.length }} primeiros erros.
      </p>
    </template>
    <template #footer>
      <el-button type="primary" @click="relatorioAberto = false">Fechar</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import type { UploadFile } from 'element-plus'
import download from '@/utils/download'
import { checkPermi } from '@/utils/permission'
import * as Api from '@/api/itsm/telefone'
import ContatoAcoes from './ContatoAcoes.vue'

defineOptions({ name: 'ItsmTelefone' })
const message = useMessage()

const loading = ref(true)
const salvando = ref(false)
const importando = ref(false)
const lista = ref<Api.Telefone[]>([])
const total = ref(0)
const setores = ref<string[]>([])
const unidades = ref<Api.UnidadeTelefone[]>([])
const q = reactive({
  pageNo: 1,
  pageSize: 20,
  busca: '',
  setor: undefined as string | undefined,
  unidadeId: undefined as number | undefined,
  emergencia: false
})
const unidadeFiltrada = computed(() => unidades.value.find((u) => u.id === q.unidadeId))
const filtrando = computed(() => !!(q.busca || q.setor || q.unidadeId || q.emergencia))

const dialogo = ref(false)
const formRef = ref()
const form = ref<Api.Telefone>({ nome: '' })
const relatorio = ref<Api.ImportacaoTelefones>()
const relatorioAberto = ref(false)

const temNumero = (_: unknown, __: unknown, feito: (e?: Error) => void) => {
  const v = form.value
  feito(
    v.ramal?.trim() || v.telefone?.trim() || v.celular?.trim()
      ? undefined
      : new Error('Informe o ramal, o telefone ou o celular')
  )
}
const emergenciaCompleta = (_: unknown, __: unknown, feito: (e?: Error) => void) => {
  feito(
    form.value.emergencia && !form.value.unidadeId
      ? new Error('Contato de emergência precisa de unidade')
      : undefined
  )
}
const celularDeEmergencia = (_: unknown, __: unknown, feito: (e?: Error) => void) => {
  feito(
    form.value.emergencia && !form.value.celular?.trim()
      ? new Error('Contato de emergência precisa de celular')
      : undefined
  )
}
const regras = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  unidadeId: [{ validator: emergenciaCompleta, trigger: 'change' }],
  ramal: [{ validator: temNumero, trigger: 'blur' }],
  celular: [{ validator: celularDeEmergencia, trigger: 'blur' }],
  email: [{ type: 'email', message: 'E-mail inválido', trigger: 'blur' }]
}

const carregar = async () => {
  loading.value = true
  try {
    const p = await Api.getTelefones({ ...q, emergencia: q.emergencia || undefined })
    lista.value = p.list
    total.value = p.total
  } finally {
    loading.value = false
  }
}
const carregarSetores = async () => {
  setores.value = await Api.getSetoresTelefone()
}
const carregarUnidades = async () => {
  unidades.value = await Api.getUnidadesTelefone()
}
const buscar = () => {
  q.pageNo = 1
  // o aviso de link fora depende do estado de agora
  carregarUnidades()
  carregar()
}
const sugerirSetores = (trecho: string, responder: (l: { value: string }[]) => void) => {
  const t = (trecho ?? '').toLowerCase()
  responder(setores.value.filter((s) => s.toLowerCase().includes(t)).map((value) => ({ value })))
}

const abrir = (t?: Api.Telefone) => {
  form.value = t ? { ...t } : { nome: '', unidadeId: q.unidadeId }
  dialogo.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const salvar = async () => {
  await formRef.value.validate()
  salvando.value = true
  try {
    await Api.salvarTelefone(form.value)
    message.success('Contato salvo')
    dialogo.value = false
    await Promise.all([carregar(), carregarSetores()])
  } finally {
    salvando.value = false
  }
}
const excluir = async (t: Api.Telefone) => {
  await message.delConfirm(`Excluir "${t.nome}" da lista?`)
  await Api.excluirTelefone(t.id!)
  message.success('Contato excluído')
  await Promise.all([carregar(), carregarSetores()])
}

const exportar = async () => download.excel(await Api.exportarTelefones(q), 'contatos.xlsx')
const baixarModelo = async () => {
  download.excel(await Api.baixarModeloTelefones(), 'modelo-contatos.xlsx')
}
const importar = async (arquivo: UploadFile) => {
  if (!arquivo.raw) return
  importando.value = true
  try {
    relatorio.value = await Api.importarTelefones(arquivo.raw)
    relatorioAberto.value = true
    await Promise.all([carregar(), carregarSetores()])
  } finally {
    importando.value = false
  }
}

onMounted(() => Promise.all([carregar(), carregarSetores(), carregarUnidades()]))
</script>

<style scoped>
.nota {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.erros {
  max-height: 280px;
  padding-left: 18px;
  overflow: auto;
  font-size: 13px;
  color: var(--el-color-danger);
}
</style>
