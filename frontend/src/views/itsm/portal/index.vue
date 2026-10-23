<template>
  <ContentWrap>
    <div class="flex flex-wrap items-center gap-12px">
      <el-button type="primary" @click="abrirNovo"><Icon class="mr-5px" icon="ep:plus" /> Abrir chamado</el-button>
      <el-radio-group v-model="somenteAbertos" @change="getList">
        <el-radio-button :value="true">Em andamento</el-radio-button>
        <el-radio-button :value="false">Todos</el-radio-button>
      </el-radio-group>
    </div>
  </ContentWrap>

  <ContentWrap>
    <el-empty v-if="!loading && !list.length" description="Você não tem chamados aqui" />
    <el-table v-else v-loading="loading" :data="list" class="cursor-pointer" @row-click="(r) => abrirDetalhe(r.id)">
      <el-table-column label="Nº" width="80"><template #default="{ row }">#{{ row.id }}</template></el-table-column>
      <el-table-column label="Assunto" min-width="260" prop="titulo" show-overflow-tooltip />
      <el-table-column label="Situação" width="200">
        <template #default="{ row }">
          <el-tag :type="corStatus(row.status)" disable-transitions>{{ row.statusRotulo }}</el-tag>
          <el-tag v-if="row.status === 5" class="ml-4px" disable-transitions effect="dark" size="small" type="success">
            sua resposta
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Categoria" min-width="180" prop="categoria" show-overflow-tooltip />
      <el-table-column label="Quem atende" min-width="150">
        <template #default="{ row }">{{ row.tecnicos.map((t) => t.nome).join(', ') || 'aguardando' }}</template>
      </el-table-column>
      <el-table-column :formatter="dateFormatter" label="Aberto em" prop="abertoEm" width="160" />
    </el-table>
    <Pagination v-model:limit="query.pageSize" v-model:page="query.pageNo" :total="total" @pagination="getList" />
  </ContentWrap>

  <!-- abrir chamado -->
  <Dialog v-model="novoVisivel" title="Abrir chamado" width="760px">
    <el-form ref="novoRef" :model="novo" :rules="regras" label-position="top">
      <el-form-item label="Sobre o que é?" prop="categoriaId">
        <el-tree-select
          v-model="novo.categoriaId"
          :data="arvoreCategorias"
          :props="{ label: 'nome', children: 'children' }"
          class="w-full"
          clearable
          filterable
          node-key="id"
          placeholder="Escolha a categoria (opcional)"
        />
      </el-form-item>
      <el-form-item label="É um problema ou um pedido?">
        <el-radio-group v-model="novo.tipo">
          <el-radio-button :value="1">Algo parou ou está com defeito</el-radio-button>
          <el-radio-button :value="2">Quero solicitar algo</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="Quanto isso atrapalha o seu trabalho?">
        <el-radio-group v-model="novo.urgencia">
          <el-radio-button :value="2">Pouco</el-radio-button>
          <el-radio-button :value="3">Médio</el-radio-button>
          <el-radio-button :value="4">Muito</el-radio-button>
          <el-radio-button :value="5">Estou parado</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="Resumo" prop="titulo">
        <el-input v-model="novo.titulo" maxlength="255" placeholder="Ex.: impressora do 2º andar não imprime" />
      </el-form-item>
      <el-form-item label="Detalhes">
        <Editor v-model="novo.descricao" directory="chamados" height="220px" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :loading="enviando" type="primary" @click="enviarNovo">Abrir chamado</el-button>
      <el-button @click="novoVisivel = false">Cancelar</el-button>
    </template>
  </Dialog>

  <!-- detalhe -->
  <el-drawer v-model="detalheVisivel" :size="largura" destroy-on-close>
    <template #header>
      <div v-if="chamado" class="min-w-0">
        <div class="truncate text-17px font-bold">#{{ chamado.id }} · {{ chamado.titulo }}</div>
        <el-tag :type="corStatus(chamado.status)" class="mt-6px" disable-transitions>{{ chamado.statusRotulo }}</el-tag>
      </div>
    </template>
    <div v-if="chamado" v-loading="carregando">
      <el-alert v-if="chamado.status === 5" :closable="false" class="mb-12px" show-icon type="success">
        <template #title>A equipe registrou uma solução. Ela resolveu o seu problema?</template>
        <el-button class="mt-6px" size="small" type="success" @click="avaliar(true)">Sim, pode fechar</el-button>
        <el-button class="mt-6px" size="small" @click="avaliar(false)">Não, continua com problema</el-button>
      </el-alert>
      <el-descriptions :column="2" border class="mb-12px" size="small">
        <el-descriptions-item label="Categoria">{{ chamado.categoria || '—' }}</el-descriptions-item>
        <el-descriptions-item label="Aberto em">{{ formatDate(chamado.abertoEm) }}</el-descriptions-item>
        <el-descriptions-item label="Quem atende">
          {{ chamado.tecnicos.map((t) => t.nome).join(', ') || 'aguardando' }}
        </el-descriptions-item>
        <el-descriptions-item label="Previsão">{{ chamado.prazoSolucao ? formatDate(chamado.prazoSolucao) : '—' }}</el-descriptions-item>
      </el-descriptions>
      <div class="mb-12px" v-html="chamado.descricao || ''"></div>
      <el-divider class="!my-12px" />
      <LinhaDoTempo :itens="linha" />
      <el-card v-if="chamado.status !== 6" class="mt-12px" shadow="never">
        <Editor :key="editorKey" v-model="resposta" directory="chamados" height="150px" />
        <div class="mt-8px text-right">
          <el-button :loading="enviando" type="primary" @click="responder">Enviar</el-button>
        </div>
      </el-card>
    </div>
  </el-drawer>
</template>

<script lang="ts" setup>
import { ElMessageBox } from 'element-plus'
import { dateFormatter, formatDate } from '@/utils/formatTime'
import { handleTree } from '@/utils/tree'
import * as ItsmApi from '@/api/itsm'
import { corStatus } from '@/api/itsm'
import LinhaDoTempo from '../components/LinhaDoTempo.vue'

defineOptions({ name: 'ItsmPortal' })
const message = useMessage()

const loading = ref(true)
const list = ref<ItsmApi.Chamado[]>([])
const total = ref(0)
const somenteAbertos = ref(true)
const query = reactive({ pageNo: 1, pageSize: 10 })
const largura = computed(() => (window.innerWidth < 1000 ? '100%' : '60%'))

const novoVisivel = ref(false)
const novoRef = ref()
const enviando = ref(false)
const arvoreCategorias = ref<any[]>([])
const vazio = () => ({ titulo: '', descricao: '', tipo: 1, urgencia: 3, categoriaId: undefined as number | undefined })
const novo = ref(vazio())
const regras = { titulo: [{ required: true, message: 'Escreva um resumo', trigger: 'blur' }] }

const detalheVisivel = ref(false)
const carregando = ref(false)
const chamado = ref<ItsmApi.Chamado>()
const linha = ref<ItsmApi.Interacao[]>([])
const resposta = ref('')
const editorKey = ref(0)

const getList = async () => {
  loading.value = true
  try {
    const data = await ItsmApi.getMeus({ ...query, somenteAbertos: somenteAbertos.value })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const abrirNovo = async () => {
  novo.value = vazio()
  novoVisivel.value = true
  arvoreCategorias.value = handleTree(await ItsmApi.getCategoriasPortal(), 'id', 'parentId')
}

const enviarNovo = async () => {
  if (!(await novoRef.value.validate().catch(() => false))) return
  enviando.value = true
  try {
    const id = await ItsmApi.abrirPortal(novo.value)
    message.success(`Chamado #${id} aberto. Você vai receber as atualizações aqui.`)
    novoVisivel.value = false
    await getList()
  } finally {
    enviando.value = false
  }
}

const carregarDetalhe = async (id: number) => {
  carregando.value = true
  try {
    ;[chamado.value, linha.value] = await Promise.all([ItsmApi.getMeu(id), ItsmApi.getMinhaLinhaDoTempo(id)])
  } finally {
    carregando.value = false
  }
}

const abrirDetalhe = async (id: number) => {
  detalheVisivel.value = true
  resposta.value = ''
  await carregarDetalhe(id)
}

const responder = async () => {
  if (!resposta.value || resposta.value === '<p><br></p>') return
  enviando.value = true
  try {
    await ItsmApi.responderPortal(chamado.value!.id, resposta.value)
    resposta.value = ''
    editorKey.value++
    await carregarDetalhe(chamado.value!.id)
    await getList()
  } finally {
    enviando.value = false
  }
}

const avaliar = async (aprovar: boolean) => {
  let comentario: string | undefined
  if (!aprovar) {
    comentario = (
      await ElMessageBox.prompt('Conte o que ainda não funciona', 'Continua com problema', {
        confirmButtonText: 'Reabrir chamado',
        cancelButtonText: 'Cancelar'
      })
    ).value
  }
  await ItsmApi.avaliarSolucaoPortal({ chamadoId: chamado.value!.id, aprovar, comentario })
  message.success(aprovar ? 'Obrigado! Chamado fechado.' : 'Chamado reaberto para a equipe.')
  await carregarDetalhe(chamado.value!.id)
  await getList()
}

onMounted(getList)
</script>
