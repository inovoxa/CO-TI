<template>
  <ContentWrap>
    <div class="flex flex-wrap items-center gap-10px">
      <el-input
        v-model="busca.q"
        class="!w-360px"
        clearable
        placeholder="Buscar na base (ex.: vpn, impressora, senha)"
        @input="buscarDepois"
      >
        <template #prefix><Icon icon="ep:search" /></template>
      </el-input>
      <template v-if="escreve">
        <el-radio-group v-model="filtro" @change="buscar">
          <el-radio-button value="todos">Todos</el-radio-button>
          <el-radio-button value="faq">FAQ</el-radio-button>
          <el-radio-button value="internos">Internos</el-radio-button>
          <el-radio-button value="rascunhos">Rascunhos</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="editar()"
          ><Icon class="mr-4px" icon="ep:plus" /> Novo artigo</el-button
        >
      </template>
    </div>
  </ContentWrap>

  <el-row :gutter="12">
    <el-col :lg="6" :span="24">
      <ContentWrap title="Categorias">
        <el-tree
          :data="arvore"
          :expand-on-click-node="false"
          :props="{ label: 'nome', children: 'children' }"
          default-expand-all
          highlight-current
          node-key="id"
          @node-click="(n: any) => escolherCategoria(n.id)"
        >
          <template #default="{ data }">
            <span class="flex-1 truncate">{{ data.nome }}</span>
            <span v-if="escreve && data.id" class="acoes-no">
              <el-button link size="small" @click.stop="editarCategoria(data)"
                ><Icon icon="ep:edit"
              /></el-button>
              <el-button link size="small" type="danger" @click.stop="excluirCategoria(data)"
                ><Icon icon="ep:delete"
              /></el-button>
            </span>
          </template>
        </el-tree>
        <el-button v-if="escreve" class="mt-8px" link type="primary" @click="editarCategoria()">
          <Icon icon="ep:plus" /> Categoria
        </el-button>
      </ContentWrap>
    </el-col>
    <el-col :lg="18" :span="24">
      <ContentWrap>
        <el-empty v-if="!loading && !lista.length" description="Nenhum artigo encontrado" />
        <div v-loading="loading">
          <div
            v-for="a in lista"
            :key="a.id"
            class="resultado"
            role="button"
            tabindex="0"
            @click="abrir(a.id)"
            @keyup.enter="abrir(a.id)"
          >
            <div class="flex flex-wrap items-center gap-6px">
              <Icon v-if="a.fixado" class="text-[var(--el-color-warning)]" icon="ep:star-filled" />
              <span class="titulo">{{ a.titulo }}</span>
              <el-tag v-if="!a.publicado" disable-transitions size="small" type="info"
                >rascunho</el-tag
              >
              <el-tag
                v-if="escreve && a.faq"
                disable-transitions
                effect="plain"
                size="small"
                type="success"
                >FAQ</el-tag
              >
              <el-tag v-if="escreve && !a.faq" disable-transitions effect="plain" size="small"
                >interno</el-tag
              >
            </div>
            <!-- trecho: HTML escapado no servidor, só com <mark> -->
            <div class="trecho" v-html="a.resumo && !busca.q ? escapar(a.resumo) : a.trecho"></div>
            <div class="meta">
              {{ a.categoria || 'Sem categoria' }} · {{ a.visualizacoes }} visualizações
              <template v-if="a.util || a.naoUtil"> · {{ a.util }} acharam útil</template>
            </div>
          </div>
        </div>
        <Pagination
          v-model:limit="busca.pageSize"
          v-model:page="busca.pageNo"
          :total="total"
          @pagination="carregar"
        />
      </ContentWrap>
    </el-col>
  </el-row>

  <!-- leitura -->
  <el-drawer v-model="leitura.visivel" :size="largura" destroy-on-close>
    <template #header>
      <div v-if="leitura.artigo" class="min-w-0">
        <div class="text-18px font-bold">{{ leitura.artigo.titulo }}</div>
        <div class="meta mt-4px">
          {{ leitura.artigo.categoria || 'Sem categoria' }} · atualizado em
          {{ formatDate(leitura.artigo.updateTime!) }}
          <template v-if="leitura.artigo.autor"> · {{ leitura.artigo.autor }}</template>
          · {{ leitura.artigo.visualizacoes }} visualizações
        </div>
      </div>
    </template>
    <template v-if="leitura.artigo">
      <div v-if="leitura.artigo.podeEditar" class="mb-12px flex gap-8px">
        <el-button @click="editar(leitura.artigo)"
          ><Icon class="mr-4px" icon="ep:edit" /> Editar</el-button
        >
        <el-button @click="verRevisoes"
          ><Icon class="mr-4px" icon="ep:clock" /> Versões ({{
            (leitura.artigo.revisao || 1) - 1
          }})</el-button
        >
        <el-button plain type="danger" @click="excluir"
          ><Icon class="mr-4px" icon="ep:delete" /> Excluir</el-button
        >
      </div>
      <el-alert
        v-if="!leitura.artigo.publicado"
        :closable="false"
        class="mb-12px"
        title="Rascunho: só quem escreve na base vê"
        type="info"
      />
      <!-- conteúdo saneado no servidor -->
      <div class="conteudo" v-html="leitura.artigo.conteudo"></div>
      <el-divider />
      <div class="flex flex-wrap items-center gap-10px">
        <span>Este artigo ajudou?</span>
        <el-button
          :type="leitura.artigo.meuVoto === true ? 'success' : 'default'"
          size="small"
          @click="votar(true)"
        >
          <Icon class="mr-4px" icon="ep:circle-check" /> Sim ({{ leitura.artigo.util }})
        </el-button>
        <el-button
          :type="leitura.artigo.meuVoto === false ? 'danger' : 'default'"
          size="small"
          @click="votar(false)"
        >
          <Icon class="mr-4px" icon="ep:circle-close" /> Não ({{ leitura.artigo.naoUtil }})
        </el-button>
        <el-link
          v-if="leitura.artigo.meuVoto === false"
          type="primary"
          @click="router.push('/meus-chamados')"
          >Abrir um chamado</el-link
        >
      </div>
      <div v-if="leitura.artigo.chamados?.length" class="meta mt-12px">
        Usado nos chamados: {{ leitura.artigo.chamados.map((c) => '#' + c).join(', ') }}
      </div>
    </template>
  </el-drawer>

  <!-- edição -->
  <Dialog
    v-model="edicao.visivel"
    :title="edicao.form.id ? 'Editar artigo' : 'Novo artigo'"
    width="960px"
  >
    <el-form ref="formRef" :model="edicao.form" :rules="regras" label-width="120px">
      <el-form-item label="Título" prop="titulo"
        ><el-input v-model="edicao.form.titulo" maxlength="255"
      /></el-form-item>
      <el-form-item label="Resumo"
        ><el-input v-model="edicao.form.resumo" maxlength="1000" type="textarea" :rows="2"
      /></el-form-item>
      <el-form-item label="Categoria">
        <el-tree-select
          v-model="edicao.form.categoriaId"
          :data="arvore[0].children"
          :props="{ label: 'nome', children: 'children' }"
          check-strictly
          class="w-full"
          clearable
          node-key="id"
        />
      </el-form-item>
      <el-form-item label="Conteúdo" prop="conteudo">
        <Editor v-model="edicao.form.conteudo" directory="base-conhecimento" height="340px" />
      </el-form-item>
      <el-form-item label="Quem vê">
        <el-radio-group v-model="edicao.form.faq">
          <el-radio-button :value="true">Todos (FAQ)</el-radio-button>
          <el-radio-button :value="false">Só a equipe técnica</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="Situação">
        <el-switch
          v-model="edicao.form.publicado"
          active-text="Publicado"
          inactive-text="Rascunho"
          inline-prompt
        />
        <el-checkbox v-model="edicao.form.fixado" class="ml-16px">Fixar no topo</el-checkbox>
      </el-form-item>
      <el-form-item label="Período">
        <el-date-picker
          v-model="edicao.periodo"
          end-placeholder="Até (opcional)"
          start-placeholder="De (opcional)"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :loading="edicao.salvando" type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="edicao.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>

  <!-- revisões -->
  <Dialog v-model="revisoes.visivel" title="Versões anteriores" width="860px">
    <el-empty v-if="!revisoes.lista.length" description="Ainda não há versões anteriores" />
    <el-collapse v-else>
      <el-collapse-item v-for="r in revisoes.lista" :key="r.id" :name="r.id">
        <template #title>
          Versão {{ r.revisao }} · {{ formatDate(r.criadoEm)
          }}<template v-if="r.autor"> · substituída por {{ r.autor }}</template>
        </template>
        <div class="font-bold">{{ r.titulo }}</div>
        <div class="conteudo" v-html="r.conteudo"></div>
        <el-button class="mt-8px" size="small" @click="restaurar(r)"
          >Voltar para esta versão</el-button
        >
      </el-collapse-item>
    </el-collapse>
  </Dialog>

  <!-- categoria -->
  <Dialog
    v-model="cat.visivel"
    :title="cat.form.id ? 'Editar categoria' : 'Nova categoria'"
    width="460px"
  >
    <el-form label-width="100px">
      <el-form-item label="Nome"><el-input v-model="cat.form.nome" maxlength="100" /></el-form-item>
      <el-form-item label="Dentro de">
        <el-tree-select
          v-model="cat.form.parentId"
          :data="[{ id: 0, nome: '(nenhuma: no primeiro nível)', children: arvore[0].children }]"
          :props="{ label: 'nome', children: 'children' }"
          check-strictly
          class="w-full"
          node-key="id"
        />
      </el-form-item>
      <el-form-item label="Ordem"
        ><el-input-number v-model="cat.form.ordem" :min="0"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="!cat.form.nome" type="primary" @click="salvarCategoria"
        >Salvar</el-button
      >
      <el-button @click="cat.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import { handleTree } from '@/utils/tree'
import { checkPermi } from '@/utils/permission'
import * as ItsmApi from '@/api/itsm'

defineOptions({ name: 'ItsmKb' })
const message = useMessage()
const route = useRoute()
const router = useRouter()

const escreve = checkPermi(['itsm:kb:update'])
const largura = computed(() => (window.innerWidth < 1000 ? '100%' : '60%'))
const loading = ref(true)
const lista = ref<ItsmApi.KbResumo[]>([])
const total = ref(0)
const categorias = ref<ItsmApi.KbCategoria[]>([])
const filtro = ref('todos')
const busca = reactive({
  q: '',
  categoriaId: undefined as number | undefined,
  pageNo: 1,
  pageSize: 20
})
const arvore = computed<any[]>(() => [
  { id: 0, nome: 'Todas', children: handleTree(categorias.value, 'id', 'parentId') }
])

const escapar = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

const carregar = async () => {
  loading.value = true
  try {
    const extra =
      filtro.value === 'faq'
        ? { faq: true }
        : filtro.value === 'internos'
          ? { faq: false }
          : filtro.value === 'rascunhos'
            ? { publicado: false }
            : {}
    const r = await ItsmApi.buscarKb({ ...busca, ...extra }, escreve)
    lista.value = r.list
    total.value = r.total
  } finally {
    loading.value = false
  }
}
const buscar = () => {
  busca.pageNo = 1
  carregar()
}
let espera: ReturnType<typeof setTimeout> | undefined
const buscarDepois = () => {
  clearTimeout(espera)
  espera = setTimeout(buscar, 300)
}
const escolherCategoria = (id: number) => {
  busca.categoriaId = id || undefined
  buscar()
}

// ---------- leitura ----------
const leitura = reactive({ visivel: false, artigo: undefined as ItsmApi.KbArtigo | undefined })
const abrir = async (id: number) => {
  leitura.artigo = await ItsmApi.lerKb(id)
  leitura.visivel = true
}
const votar = async (util: boolean) => {
  await ItsmApi.votarKb(leitura.artigo!.id!, util)
  leitura.artigo = await ItsmApi.lerKb(leitura.artigo!.id!, false)
  if (util) message.success('Obrigado!')
}
const excluir = async () => {
  await message.delConfirm(`Excluir o artigo "${leitura.artigo!.titulo}"?`)
  await ItsmApi.excluirKb(leitura.artigo!.id!)
  leitura.visivel = false
  await carregar()
}

// ---------- edição ----------
const formRef = ref()
const vazio = (): ItsmApi.KbArtigo => ({
  titulo: '',
  conteudo: '',
  faq: true,
  publicado: true,
  fixado: false
})
const edicao = reactive({ visivel: false, salvando: false, form: vazio(), periodo: [] as string[] })
const regras = {
  titulo: [{ required: true, message: 'Informe o título', trigger: 'blur' }],
  conteudo: [{ required: true, message: 'Escreva o conteúdo', trigger: 'blur' }]
}
const paraTexto = (v?: number | string) => (v ? formatDate(new Date(v), 'YYYY-MM-DD HH:mm:ss') : '')
const editar = (a?: ItsmApi.KbArtigo) => {
  edicao.form = a ? { ...a } : vazio()
  edicao.periodo =
    a?.inicioPublicacao || a?.fimPublicacao
      ? [paraTexto(a.inicioPublicacao), paraTexto(a.fimPublicacao)]
      : []
  edicao.visivel = true
}
const salvar = async () => {
  if (!(await formRef.value.validate().catch(() => false))) return
  edicao.salvando = true
  try {
    const f = edicao.form
    const id = await ItsmApi.salvarKb({
      id: f.id,
      titulo: f.titulo,
      resumo: f.resumo,
      conteudo: f.conteudo,
      categoriaId: f.categoriaId || undefined,
      faq: f.faq,
      publicado: f.publicado,
      fixado: f.fixado,
      inicioPublicacao: edicao.periodo?.[0] || undefined,
      fimPublicacao: edicao.periodo?.[1] || undefined
    })
    message.success('Artigo salvo')
    edicao.visivel = false
    await carregar()
    if (leitura.visivel) leitura.artigo = await ItsmApi.lerKb(id, false)
  } finally {
    edicao.salvando = false
  }
}

// ---------- revisões ----------
const revisoes = reactive({ visivel: false, lista: [] as ItsmApi.KbRevisao[] })
const verRevisoes = async () => {
  revisoes.lista = await ItsmApi.revisoesKb(leitura.artigo!.id!)
  revisoes.visivel = true
}
const restaurar = async (r: ItsmApi.KbRevisao) => {
  await message.confirm(`Voltar o artigo para a versão ${r.revisao}? A versão atual fica guardada.`)
  await ItsmApi.restaurarKb(r.id)
  revisoes.visivel = false
  leitura.artigo = await ItsmApi.lerKb(leitura.artigo!.id!, false)
  await carregar()
}

// ---------- categorias ----------
const cat = reactive({
  visivel: false,
  form: { nome: '', parentId: 0, ordem: 0 } as Partial<ItsmApi.KbCategoria>
})
const carregarCategorias = async () => (categorias.value = await ItsmApi.getKbCategorias())
const editarCategoria = (c?: ItsmApi.KbCategoria) => {
  cat.form = c
    ? { id: c.id, nome: c.nome, parentId: c.parentId, ordem: c.ordem }
    : { nome: '', parentId: busca.categoriaId || 0, ordem: 0 }
  cat.visivel = true
}
const salvarCategoria = async () => {
  await ItsmApi.salvarKbCategoria(cat.form)
  cat.visivel = false
  await carregarCategorias()
}
const excluirCategoria = async (c: ItsmApi.KbCategoria) => {
  await message.delConfirm(`Excluir a categoria "${c.nome}"?`)
  await ItsmApi.excluirKbCategoria(c.id)
  await carregarCategorias()
}

onMounted(async () => {
  await Promise.all([carregarCategorias(), carregar()])
  const artigo = Number(route.query.artigo)
  if (artigo) await abrir(artigo)
})
watch(
  () => route.query.artigo,
  (a) => a && abrir(Number(a))
)
</script>

<style scoped>
.resultado {
  padding: 12px 6px;
  cursor: pointer;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.resultado:hover,
.resultado:focus-visible {
  outline: none;
  background: var(--el-fill-color-light);
}
.titulo {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-color-primary);
}
.trecho {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.5;
}
.trecho :deep(mark) {
  padding: 0 2px;
  color: inherit;
  background: var(--el-color-warning-light-7);
}
.meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.acoes-no {
  display: none;
}
:deep(.el-tree-node__content:hover) .acoes-no {
  display: inline-flex;
}
.conteudo :deep(img) {
  max-width: 100%;
}
.conteudo :deep(table) {
  border-collapse: collapse;
}
.conteudo :deep(td),
.conteudo :deep(th) {
  padding: 4px 8px;
  border: 1px solid var(--el-border-color);
}
</style>
