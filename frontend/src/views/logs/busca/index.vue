<!-- Busca de logs (o "Search" do Graylog): consulta, histograma, campos e mensagens. -->
<template>
  <div class="busca">
    <div class="filtro">
      <el-select v-model="fluxo" class="!w-170px" placeholder="Todos os fluxos">
        <el-option v-for="f in Api.FLUXOS" :key="f.valor" :label="f.rotulo" :value="f.valor" />
      </el-select>
      <el-select v-model="periodo" class="!w-190px" :disabled="!!intervalo">
        <el-option v-for="p in Api.PERIODOS" :key="p.valor" :label="p.rotulo" :value="p.valor" />
      </el-select>
      <el-input
        v-model="digitado"
        class="consulta"
        clearable
        placeholder="Ex.: event_id:4625 AND usuario:joao*   ·   hostname:&quot;www.youtube.com&quot;   ·   texto livre"
        @keyup.enter="buscar"
      />
      <el-button type="success" @click="buscar"><Icon class="mr-4px" icon="ep:search" />Buscar</el-button>
    </div>
    <div v-if="intervalo" class="intervalo">
      Intervalo: {{ quando(intervalo.de) }} até {{ quando(intervalo.ate) }}
      <el-button link type="primary" @click="limparIntervalo">usar o período</el-button>
    </div>
    <div v-if="erro" class="erro"><Icon icon="ep:warning" /> {{ erro }}</div>

    <div class="corpo">
      <!-- campos -->
      <ContentWrap class="lateral">
        <div class="titulo-lateral">Campos</div>
        <el-input v-model="filtroCampos" class="mb-6px" clearable placeholder="Procurar" size="small" />
        <el-scrollbar max-height="calc(100vh - 330px)">
          <div v-for="c in camposVisiveis" :key="c.valor" class="campo" @click="abrirRanking(c.valor)">
            <span class="nome">{{ c.valor }}</span>
            <span class="qtd">{{ c.total }}</span>
          </div>
          <div class="text-11px text-[var(--el-text-color-secondary)] mt-6px">
            Das 500 mensagens mais recentes. Clique para ver os valores mais comuns.
          </div>
        </el-scrollbar>
      </ContentWrap>

      <div class="principal">
        <ContentWrap>
          <div class="resumo">
            <span>
              <b>{{ totalTexto }}</b>
              <span v-if="mensagens" class="ms"> · {{ mensagens.ms }} ms</span>
            </span>
          </div>
          <div class="grafico">
            <QuadroHistograma v-if="consultaAplicada !== undefined && !intervalo" :quadro="quadroHistograma" />
          </div>
        </ContentWrap>
        <ContentWrap v-loading="carregando">
          <ListaMensagens :mensagens="mensagens?.lista ?? []" :colunas="[]" :base="filtroAtual" />
          <el-pagination
            v-if="(mensagens?.total ?? 0) > tamanho"
            v-model:current-page="pagina"
            class="justify-center pt-10px"
            :page-size="tamanho"
            :total="Math.min(mensagens?.total ?? 0, 10000)"
            background
            layout="prev, pager, next"
            @current-change="carregarMensagens"
          />
        </ContentWrap>
      </div>
    </div>

    <!-- ranking rápido de um campo -->
    <el-dialog v-model="ranking.aberto" :title="`Valores mais comuns: ${ranking.campo}`" width="560px" append-to-body>
      <div class="h-420px">
        <QuadroRanking v-if="ranking.aberto" :quadro="quadroRanking" />
      </div>
    </el-dialog>

    <Gavetas ref="gavetas" />
  </div>
</template>

<script lang="ts" setup>
import * as Api from '@/api/logs'
import { CONTEXTO, quando } from '../contexto'
import Gavetas from '../components/Gavetas.vue'
import ListaMensagens from '../components/ListaMensagens.vue'
import QuadroHistograma from '../components/QuadroHistograma.vue'
import QuadroRanking from '../components/QuadroRanking.vue'

defineOptions({ name: 'LogsBusca' })

const route = useRoute()
const router = useRouter()

const fluxo = ref<Api.Fluxo | ''>('')
const periodo = ref('1h')
const digitado = ref('')
const consultaAplicada = ref<string>()
const intervalo = ref<{ de: string; ate: string }>()
const erro = ref('')
const carregando = ref(false)
const mensagens = ref<Api.Mensagens>()
const campos = ref<Api.Item[]>([])
const filtroCampos = ref('')
const pagina = ref(1)
const tamanho = 50
const gavetas = ref<InstanceType<typeof Gavetas>>()

// o contexto dos quadros: aqui "filtro geral" é a própria consulta da busca
const filtroGeral = ref('')
const periodoGeral = ref('')
const atualizacao = ref(0)
provide(CONTEXTO, {
  filtroGeral,
  periodoGeral,
  atualizacao,
  filtrar: (campo, valor, excluir) => {
    const t = (excluir ? '-' : '') + Api.termo(campo, valor)
    digitado.value = digitado.value.trim() ? `${digitado.value.trim()} AND ${t}` : t
    buscar()
  },
  mostrarMensagens: (titulo, f) => gavetas.value?.mostrarMensagens(titulo, f),
  abrirMensagem: (m) => gavetas.value?.abrirMensagem(m)
})

const filtroAtual = computed<Api.Filtro>(() => ({
  consulta: consultaAplicada.value || '',
  fluxo: fluxo.value,
  ...(intervalo.value ? { de: intervalo.value.de, ate: intervalo.value.ate } : { periodo: periodo.value })
}))

const quadroHistograma = computed<Api.Quadro>(() => ({
  id: 'busca-h',
  tipo: 'histograma',
  titulo: 'Busca',
  consulta: consultaAplicada.value,
  fluxo: fluxo.value,
  periodo: periodo.value
}))

const ranking = reactive({ aberto: false, campo: '' })
const quadroRanking = computed<Api.Quadro>(() => ({
  id: 'busca-r',
  tipo: 'ranking',
  titulo: ranking.campo,
  campo: ranking.campo,
  consulta: consultaAplicada.value,
  fluxo: fluxo.value,
  periodo: periodo.value,
  limite: 25
}))
const abrirRanking = (campo: string) => {
  ranking.campo = campo
  ranking.aberto = true
}

const camposVisiveis = computed(() =>
  campos.value.filter((c) => !filtroCampos.value || c.valor.toLowerCase().includes(filtroCampos.value.toLowerCase()))
)

const totalTexto = computed(() => {
  const m = mensagens.value
  if (!m) return 'Buscando…'
  return `${m.total > m.limiteTotal ? 'Mais de ' : ''}${Api.numero(Math.min(m.total, m.limiteTotal))} mensagens`
})

const carregarMensagens = async () => {
  carregando.value = true
  erro.value = ''
  try {
    mensagens.value = await Api.getMensagens(filtroAtual.value, pagina.value, tamanho)
  } catch (e: any) {
    erro.value = e?.msg || e?.message || 'Consulta inválida'
  } finally {
    carregando.value = false
  }
}

const buscar = async () => {
  consultaAplicada.value = digitado.value.trim()
  pagina.value = 1
  router.replace({
    query: {
      q: consultaAplicada.value || undefined,
      fluxo: fluxo.value || undefined,
      periodo: intervalo.value ? undefined : periodo.value,
      de: intervalo.value?.de,
      ate: intervalo.value?.ate
    }
  })
  atualizacao.value++
  await carregarMensagens()
  Api.getCampos(filtroAtual.value)
    .then((c) => (campos.value = c))
    .catch(() => (campos.value = []))
}

const limparIntervalo = () => {
  intervalo.value = undefined
  buscar()
}

let pronto = false
watch([fluxo, periodo], () => pronto && buscar())

// vem de um painel ("Abrir na busca") ou de um link
const lerRota = () => {
  const q = route.query
  digitado.value = (q.q as string) || ''
  if (q.fluxo) fluxo.value = q.fluxo as Api.Fluxo
  if (q.periodo) periodo.value = q.periodo as string
  intervalo.value = q.de ? { de: q.de as string, ate: (q.ate as string) || new Date().toISOString().slice(0, 19) } : undefined
}
onMounted(async () => {
  lerRota()
  await buscar()
  pronto = true
})
</script>

<style lang="scss" scoped>
.filtro {
  display: flex;
  margin-bottom: 8px;
  flex-wrap: wrap;
  gap: 8px;

  .consulta {
    min-width: 280px;
    font-family: Consolas, 'Courier New', monospace;
    flex: 1;
  }
}

.intervalo,
.erro {
  margin-bottom: 8px;
  font-size: 12px;
}

.erro {
  color: var(--el-color-danger);
}

.corpo {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.lateral {
  width: 230px;
  flex-shrink: 0;

  :deep(.el-card__body) {
    padding: 10px;
  }
}

.titulo-lateral {
  margin-bottom: 6px;
  font-weight: 600;
}

.campo {
  display: flex;
  padding: 3px 4px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  cursor: pointer;
  border-radius: 3px;
  justify-content: space-between;
  gap: 6px;

  &:hover {
    color: var(--el-color-primary);
    background: var(--el-fill-color-light);
  }

  .nome {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .qtd {
    color: var(--el-text-color-secondary);
  }
}

.principal {
  min-width: 0;
  flex: 1;
}

.resumo {
  margin-bottom: 4px;
  font-size: 13px;

  .ms {
    color: var(--el-text-color-secondary);
  }
}

.grafico {
  height: 200px;
}

@media (width <= 900px) {
  .corpo {
    flex-direction: column;
  }

  .lateral {
    width: 100%;
  }
}
</style>
