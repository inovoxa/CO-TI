<!-- Gavetas laterais das telas de logs: mensagens de um valor/intervalo e o detalhe (todos os campos) de uma mensagem. -->
<template>
  <el-drawer v-model="lista.aberta" :title="lista.titulo" size="62%" append-to-body destroy-on-close>
    <div class="gaveta">
      <div class="barra">
        <span class="text-12px text-[var(--el-text-color-secondary)]">
          {{ total }} · <code>{{ consultaVisivel || 'tudo' }}</code>
        </span>
        <el-button link type="primary" @click="abrirNaBusca"><Icon class="mr-4px" icon="ep:search" />Abrir na busca</el-button>
      </div>
      <el-scrollbar class="flex-1" v-loading="lista.carregando">
        <ListaMensagens :mensagens="lista.dados?.lista ?? []" :colunas="[]" :base="lista.filtro" />
      </el-scrollbar>
      <el-pagination
        v-if="(lista.dados?.total ?? 0) > 50"
        v-model:current-page="lista.pagina"
        class="justify-center pt-8px"
        :page-size="50"
        :total="Math.min(lista.dados?.total ?? 0, 10000)"
        background
        layout="prev, pager, next"
        size="small"
        @current-change="carregarLista"
      />
    </div>
  </el-drawer>

  <el-drawer v-model="detalhe.aberta" size="48%" append-to-body :title="detalhe.m ? quando(detalhe.m.momento) : ''">
    <div v-if="detalhe.m" class="detalhe">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="Origem">
          <ValorClicavel campo="origem" :valor="detalhe.m.origem" :base="baseDetalhe" />
        </el-descriptions-item>
        <el-descriptions-item label="Fluxo">
          <ValorClicavel campo="fluxo" :valor="detalhe.m.fluxo" :base="baseDetalhe" />
        </el-descriptions-item>
        <el-descriptions-item label="Nível">
          {{ detalhe.m.nivel }} · {{ Api.NIVEIS[detalhe.m.nivel] }}
        </el-descriptions-item>
        <el-descriptions-item label="Programa">
          <ValorClicavel v-if="detalhe.m.aplicacao" campo="aplicacao" :valor="detalhe.m.aplicacao" :base="baseDetalhe" />
        </el-descriptions-item>
        <el-descriptions-item label="Recebida de" :span="2">{{ detalhe.m.ip }}</el-descriptions-item>
      </el-descriptions>
      <div class="secao">Mensagem</div>
      <pre class="mensagem">{{ detalhe.m.mensagem }}</pre>
      <div class="secao">Campos ({{ Object.keys(detalhe.m.campos || {}).length }})</div>
      <el-input v-model="detalhe.busca" class="mb-6px" clearable placeholder="Procurar campo" size="small" />
      <table class="campos">
        <tbody>
          <tr v-for="[k, v] in camposDetalhe" :key="k">
            <th>{{ k }}</th>
            <td><ValorClicavel :campo="k" :valor="String(v)" :base="baseDetalhe" /></td>
          </tr>
        </tbody>
      </table>
    </div>
  </el-drawer>
</template>

<script lang="ts" setup>
import * as Api from '@/api/logs'
import { quando } from '../contexto'
import ListaMensagens from './ListaMensagens.vue'
import ValorClicavel from './ValorClicavel.vue'

const router = useRouter()

const lista = reactive({
  aberta: false,
  titulo: '',
  filtro: {} as Api.Filtro,
  pagina: 1,
  carregando: false,
  dados: undefined as Api.Mensagens | undefined
})

const consultaVisivel = computed(() => Api.juntar(lista.filtro.consulta, lista.filtro.filtroGeral))
const total = computed(() => {
  const d = lista.dados
  if (!d) return ''
  return `${d.total > d.limiteTotal ? 'mais de ' : ''}${Api.numero(Math.min(d.total, d.limiteTotal))} mensagens`
})

const carregarLista = async () => {
  lista.carregando = true
  try {
    lista.dados = await Api.getMensagens(lista.filtro, lista.pagina, 50)
  } finally {
    lista.carregando = false
  }
}

const mostrarMensagens = (titulo: string, filtro: Api.Filtro) => {
  lista.titulo = titulo
  lista.filtro = filtro
  lista.pagina = 1
  lista.dados = undefined
  lista.aberta = true
  carregarLista()
}

const abrirNaBusca = () => {
  const f = lista.filtro
  lista.aberta = false
  router.push({
    name: 'LogsBusca',
    query: {
      q: consultaVisivel.value || undefined,
      fluxo: f.fluxo || undefined,
      periodo: f.periodo || undefined,
      de: f.de || undefined,
      ate: f.ate || undefined
    }
  })
}

const detalhe = reactive({ aberta: false, m: undefined as Api.Mensagem | undefined, busca: '' })
const baseDetalhe = computed<Api.Filtro>(() => ({ periodo: '1d', fluxo: detalhe.m?.fluxo }))
const camposDetalhe = computed(() =>
  Object.entries(detalhe.m?.campos ?? {})
    .filter(([k]) => !detalhe.busca || k.toLowerCase().includes(detalhe.busca.toLowerCase()))
    .sort(([a], [b]) => a.localeCompare(b))
)

const abrirMensagem = (m: Api.Mensagem) => {
  detalhe.m = m
  detalhe.busca = ''
  detalhe.aberta = true
}

defineExpose({ mostrarMensagens, abrirMensagem })
</script>

<style lang="scss" scoped>
.gaveta {
  display: flex;
  height: 100%;
  flex-direction: column;
}

.barra {
  display: flex;
  padding-bottom: 8px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;

  code {
    font-size: 11px;
  }
}

.secao {
  margin: 14px 0 6px;
  font-size: 13px;
  font-weight: 600;
}

.mensagem {
  max-height: 220px;
  padding: 8px 10px;
  margin: 0;
  overflow: auto;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  color: #2f6fb5;
  word-break: break-all;
  white-space: pre-wrap;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.campos {
  width: 100%;
  font-size: 12px;
  border-collapse: collapse;

  th,
  td {
    padding: 4px 8px;
    text-align: left;
    vertical-align: top;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  th {
    width: 34%;
    font-family: Consolas, 'Courier New', monospace;
    font-weight: 600;
    color: var(--el-text-color-secondary);
    word-break: break-all;
  }

  td {
    word-break: break-all;
  }
}

:global(html.dark .detalhe .mensagem) {
  color: #7fb2ea;
}
</style>
