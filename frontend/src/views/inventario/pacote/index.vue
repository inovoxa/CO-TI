<template>
  <ContentWrap>
    <el-alert :closable="false" class="mb-12px" show-icon type="warning">
      Os pacotes executam comandos nas máquinas com agente OCS. Teste num computador antes de
      implantar para uma unidade inteira. Criar, enviar e implantar fica registrado na auditoria.
    </el-alert>
    <el-button v-if="pode" plain type="primary" @click="abrirForm()">
      <Icon class="mr-5px" icon="ep:plus" /> Novo pacote
    </el-button>
  </ContentWrap>

  <ContentWrap>
    <el-table
      v-loading="loading"
      :data="lista"
      class="cursor-pointer"
      highlight-current-row
      @row-click="(r) => selecionar(r)"
    >
      <el-table-column label="Pacote" min-width="200">
        <template #default="{ row }">
          <span class="font-medium">{{ row.nome }}</span>
          <el-tag v-if="!row.ativo" class="ml-4px" disable-transitions size="small" type="info"
            >inativo</el-tag
          >
          <div class="text-12px text-[var(--el-text-color-secondary)]">
            {{ ACOES[row.acao] }} · {{ row.plataforma }}
            <template v-if="row.comando"> · {{ row.comando }}</template>
            <template v-if="row.caminho"> · {{ row.caminho }}</template>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="Arquivo" min-width="180">
        <template #default="{ row }">
          <template v-if="row.fragmentos">
            {{ row.arquivoNome }} · {{ tamanho(row.tamanho) }}
          </template>
          <el-tag v-else disable-transitions size="small" type="warning">sem arquivo</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Máquinas" min-width="260">
        <template #default="{ row }">
          <el-tag
            v-for="(n, s) in row.contagens"
            :key="s"
            :type="SITUACOES[s]?.cor"
            class="mr-4px"
            disable-transitions
            size="small"
            >{{ SITUACOES[s]?.rotulo ?? s }}: {{ n }}</el-tag
          >
          <span v-if="!Object.keys(row.contagens || {}).length">—</span>
        </template>
      </el-table-column>
      <el-table-column v-if="pode" width="250">
        <template #default="{ row }">
          <el-button link type="primary" @click.stop="abrirForm(row)">Editar</el-button>
          <el-button link type="primary" @click.stop="escolherArquivo(row)"
            >Enviar arquivo</el-button
          >
          <el-button
            :disabled="!row.fragmentos || !row.ativo"
            link
            type="success"
            @click.stop="abrirImplantar(row)"
            >Implantar</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <input ref="arquivoRef" class="hidden" type="file" @change="enviarArquivo" />
    <el-progress
      v-if="envio.percentual !== null"
      :percentage="envio.percentual"
      :stroke-width="10"
      class="mt-10px"
    />
  </ContentWrap>

  <ContentWrap v-if="selecionado" :title="`Máquinas · ${selecionado.nome}`">
    <el-form :inline="true" class="-mb-15px" label-width="0">
      <el-form-item>
        <el-select
          v-model="iq.situacao"
          class="!w-180px"
          clearable
          placeholder="Situação"
          @change="carregarImplantacoes"
        >
          <el-option v-for="(s, k) in SITUACOES" :key="k" :label="s.rotulo" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="carregarImplantacoes"><Icon icon="ep:refresh" /></el-button>
      </el-form-item>
    </el-form>
    <el-table :data="implantacoes" size="small">
      <el-table-column label="Máquina" min-width="160">
        <template #default="{ row }">
          <el-link
            type="primary"
            @click="router.push({ path: '/inventario/ativos', query: { id: row.ativoId } })"
            >{{ row.ativoNome }}</el-link
          >
        </template>
      </el-table-column>
      <el-table-column label="Situação" width="130">
        <template #default="{ row }">
          <el-tag :type="SITUACOES[row.situacao]?.cor" disable-transitions size="small">{{
            SITUACOES[row.situacao]?.rotulo ?? row.situacao
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Resposta do agente" min-width="160" prop="codigo" />
      <el-table-column label="Pedido" width="140">
        <template #default="{ row }">{{ quando(row.pedidoEm) }}</template>
      </el-table-column>
      <el-table-column label="Agente recebeu" width="140">
        <template #default="{ row }">{{ quando(row.notificadoEm) }}</template>
      </el-table-column>
      <el-table-column label="Concluído" width="140">
        <template #default="{ row }">{{ quando(row.concluidoEm) }}</template>
      </el-table-column>
      <el-table-column v-if="pode" width="90">
        <template #default="{ row }">
          <el-button
            v-if="['aguardando', 'notificado'].includes(row.situacao)"
            link
            type="danger"
            @click="cancelar(row)"
            >Cancelar</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="iq.pageSize"
      v-model:page="iq.pageNo"
      :total="itotal"
      @pagination="carregarImplantacoes"
    />
  </ContentWrap>

  <!-- pacote -->
  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Editar pacote' : 'Novo pacote'"
    width="640px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="140px">
      <el-form-item label="Nome" prop="nome"
        ><el-input v-model="form.dados.nome" maxlength="150"
      /></el-form-item>
      <el-form-item label="Plataforma">
        <el-radio-group v-model="form.dados.plataforma">
          <el-radio-button value="windows">Windows</el-radio-button>
          <el-radio-button value="linux">Linux</el-radio-button>
          <el-radio-button value="macos">macOS</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="Ação">
        <el-select v-model="form.dados.acao" class="w-full">
          <el-option v-for="(r, k) in ACOES" :key="k" :label="r" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item
        v-if="form.dados.acao !== 'STORE'"
        :label="form.dados.acao === 'EXECUTE' ? 'Comando' : 'Arquivo'"
      >
        <el-input
          v-model="form.dados.comando"
          :placeholder="
            form.dados.acao === 'EXECUTE' ? 'Ex.: msiexec /i app.msi /qn' : 'Ex.: setup.exe'
          "
          maxlength="1000"
        />
      </el-form-item>
      <el-form-item v-else label="Pasta de destino">
        <el-input
          v-model="form.dados.caminho"
          maxlength="500"
          placeholder="Ex.: C:\Programas\Modelos"
        />
      </el-form-item>
      <el-form-item label="Prioridade">
        <el-input-number
          v-model="form.dados.prioridade"
          :max="10"
          :min="0"
          controls-position="right"
        />
        <span class="ml-6px text-12px text-[var(--el-text-color-secondary)]"
          >0 = primeiro; 5 = normal</span
        >
      </el-form-item>
      <el-form-item label="Avisar o usuário">
        <el-switch v-model="form.dados.notificar" />
        <el-input
          v-if="form.dados.notificar"
          v-model="form.dados.notificarTexto"
          class="mt-6px"
          maxlength="500"
          placeholder="Mensagem mostrada antes de instalar"
        />
      </el-form-item>
      <el-form-item label="Descrição">
        <el-input v-model="form.dados.descricao" :rows="2" type="textarea" />
      </el-form-item>
      <el-form-item label="Ativo"><el-switch v-model="form.dados.ativo" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>

  <!-- implantar -->
  <Dialog v-model="impl.visivel" :title="`Implantar ${impl.pacote?.nome ?? ''}`" width="600px">
    <el-radio-group v-model="impl.modo" class="mb-12px">
      <el-radio-button value="maquinas">Máquinas</el-radio-button>
      <el-radio-button value="unidade">Unidade inteira</el-radio-button>
    </el-radio-group>
    <el-select
      v-if="impl.modo === 'maquinas'"
      v-model="impl.ativoIds"
      :loading="impl.buscando"
      :remote-method="buscarMaquinas"
      class="w-full"
      filterable
      multiple
      placeholder="Nome, IP ou usuário (só máquinas com agente OCS)"
      remote
    >
      <el-option
        v-for="a in impl.opcoes"
        :key="a.id"
        :label="`${a.nome} · ${a.soNome ?? ''}`"
        :value="a.id"
      />
    </el-select>
    <el-tree-select
      v-else
      v-model="impl.deptId"
      :data="depts"
      :props="defaultProps"
      check-strictly
      class="w-full"
      node-key="id"
      placeholder="Unidade (inclui as subunidades)"
    />
    <div class="mt-8px text-12px text-[var(--el-text-color-secondary)]">
      As máquinas recebem o pacote no próximo contato do agente. Só entram máquinas em uso, com
      agente OCS e da plataforma do pacote.
    </div>
    <template #footer>
      <el-button type="primary" @click="implantar">Implantar</el-button>
      <el-button @click="impl.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Inv from '@/api/inventario'
import * as DeptApi from '@/api/system/dept'
import { checkPermi } from '@/utils/permission'
import { defaultProps, handleTree } from '@/utils/tree'

defineOptions({ name: 'InventarioPacote' })
const message = useMessage()
const router = useRouter()
const pode = checkPermi(['inventario:pacote:update'])
const regras = { nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }] }
const ACOES: Record<string, string> = {
  EXECUTE: 'Executar comando',
  LAUNCH: 'Abrir arquivo do pacote',
  STORE: 'Só copiar para uma pasta'
}
const SITUACOES: Record<string, { rotulo: string; cor: any }> = {
  aguardando: { rotulo: 'Aguardando', cor: 'info' },
  notificado: { rotulo: 'Agente recebeu', cor: 'warning' },
  sucesso: { rotulo: 'Sucesso', cor: 'success' },
  erro: { rotulo: 'Erro', cor: 'danger' },
  cancelado: { rotulo: 'Cancelado', cor: 'info' }
}

const lista = ref<Inv.Pacote[]>([])
const loading = ref(true)
const selecionado = ref<Inv.Pacote>()
const implantacoes = ref<Inv.Implantacao[]>([])
const itotal = ref(0)
const iq = reactive({ pageNo: 1, pageSize: 20, situacao: undefined as string | undefined })
const depts = ref<any[]>([])
const formRef = ref()
const form = reactive({ visivel: false, dados: {} as any })
const arquivoRef = ref<HTMLInputElement>()
const envio = reactive({
  pacote: undefined as Inv.Pacote | undefined,
  percentual: null as number | null
})
const impl = reactive({
  visivel: false,
  pacote: undefined as Inv.Pacote | undefined,
  modo: 'maquinas',
  ativoIds: [] as number[],
  deptId: undefined as number | undefined,
  opcoes: [] as Inv.Ativo[],
  buscando: false
})

const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—'
const tamanho = (b?: number) =>
  !b ? '—' : b > 1048576 ? `${(b / 1048576).toFixed(1)} MB` : `${Math.ceil(b / 1024)} KB`

const carregar = async () => {
  loading.value = true
  try {
    lista.value = await Inv.getPacotes()
  } finally {
    loading.value = false
  }
}
const selecionar = async (p: Inv.Pacote) => {
  selecionado.value = p
  iq.pageNo = 1
  await carregarImplantacoes()
}
const carregarImplantacoes = async () => {
  if (!selecionado.value) return
  const r = await Inv.getImplantacoes({ ...iq, pacoteId: selecionado.value.id })
  implantacoes.value = r.list
  itotal.value = r.total
}

const abrirForm = (p?: Inv.Pacote) => {
  form.dados = p
    ? { ...p }
    : {
        nome: '',
        plataforma: 'windows',
        acao: 'EXECUTE',
        prioridade: 5,
        notificar: false,
        ativo: true
      }
  form.visivel = true
}
const salvar = async () => {
  await formRef.value.validate()
  await Inv.salvarPacote(form.dados)
  message.success('Pacote salvo')
  form.visivel = false
  await carregar()
}

const escolherArquivo = (p: Inv.Pacote) => {
  envio.pacote = p
  arquivoRef.value!.value = ''
  arquivoRef.value!.click()
}
const enviarArquivo = async (e: Event) => {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (!f || !envio.pacote) return
  envio.percentual = 0
  try {
    await Inv.enviarArquivoPacote(envio.pacote.id!, f, (p) => (envio.percentual = p))
    message.success('Arquivo enviado')
    await carregar()
  } finally {
    envio.percentual = null
  }
}

const abrirImplantar = async (p: Inv.Pacote) => {
  Object.assign(impl, {
    visivel: true,
    pacote: p,
    modo: 'maquinas',
    ativoIds: [],
    deptId: undefined,
    opcoes: []
  })
  if (!depts.value.length) depts.value = handleTree(await DeptApi.getSimpleDeptList())
}
const buscarMaquinas = async (texto: string) => {
  if (!texto) return
  impl.buscando = true
  try {
    impl.opcoes = (
      await Inv.getAtivos({
        busca: texto,
        origem: 'ocs',
        situacao: 'em_uso',
        pageNo: 1,
        pageSize: 30
      })
    ).list
  } finally {
    impl.buscando = false
  }
}
const implantar = async () => {
  const req =
    impl.modo === 'maquinas'
      ? { pacoteId: impl.pacote!.id!, ativoIds: impl.ativoIds }
      : { pacoteId: impl.pacote!.id!, deptId: impl.deptId }
  if (impl.modo === 'unidade') {
    await message.confirm(`Implantar ${impl.pacote!.nome} em todas as máquinas da unidade?`)
  }
  const r = await Inv.implantarPacote(req)
  message.success(
    `${r.pedidos} máquina(s) vão receber o pacote` +
      (r.jaPendentes ? `; ${r.jaPendentes} já estavam na fila` : '') +
      (r.ignorados ? `; ${r.ignorados} ignorada(s)` : '')
  )
  impl.visivel = false
  await carregar()
  await selecionar(impl.pacote!)
}
const cancelar = async (i: Inv.Implantacao) => {
  await message.confirm(`Cancelar o pacote em ${i.ativoNome}?`)
  await Inv.cancelarImplantacao(i.id)
  await carregarImplantacoes()
  await carregar()
}

onMounted(carregar)
</script>
