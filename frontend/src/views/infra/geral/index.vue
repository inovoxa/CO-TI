<template>
  <div v-loading="carregando">
    <ContentWrap v-if="c">
      <el-alert
        v-if="c.percentual >= 80"
        :closable="false"
        :title="`Espaço em ${c.percentual}%. Veja abaixo o que mais cresce e ajuste os prazos de retenção.`"
        :type="c.percentual >= 90 ? 'error' : 'warning'"
        class="mb-16px"
        show-icon
      />
      <div class="numeros">
        <div>
          <div class="rotulo">Espaço livre</div>
          <div class="valor">{{ tamanho(c.livreBytes) }}</div>
          <div class="ajuda">
            {{ c.percentual }}% usado de {{ tamanho(c.totalBytes) }}
            {{ c.pelaReserva ? '(reserva do CO-TI)' : '(informado pelo servidor)' }}
          </div>
        </div>
        <div>
          <div class="rotulo">Dados do CO-TI</div>
          <div class="valor">{{ tamanho(c.dadosBytes) }}</div>
          <div class="ajuda">banco, anexos e logs</div>
        </div>
        <div>
          <div class="rotulo">Crescimento por dia</div>
          <div class="valor">{{ temHistorico ? tamanho(c.crescimentoPorDia) : '—' }}</div>
          <div class="ajuda">{{ textoHistorico }}</div>
        </div>
        <div>
          <div class="rotulo">Previsão até encher</div>
          <div class="valor">{{ previsao }}</div>
          <div class="ajuda">{{ c.diasAteEncher == null ? 'sem crescimento medido' : 'mantido o ritmo atual' }}</div>
        </div>
      </div>
      <el-progress
        :percentage="c.percentual"
        :show-text="false"
        :status="c.percentual >= 90 ? 'exception' : c.percentual >= 80 ? 'warning' : undefined"
        :stroke-width="8"
        class="mt-16px"
      />
      <p v-if="!c.pelaReserva" class="ajuda mt-8px">
        Em Docker Desktop e em alguns servidores, o sistema informa um disco maior que o real. Nesses casos, defina o "Espaço
        reservado ao CO-TI" abaixo.
      </p>
    </ContentWrap>

    <ContentWrap v-if="c" title="O que ocupa espaço">
      <el-table :data="areasOrdenadas" :stripe="true">
        <el-table-column label="Área" min-width="200" prop="rotulo" />
        <el-table-column align="right" label="Tamanho" width="120">
          <template #default="{ row }">{{ tamanho(row.bytes) }}</template>
        </el-table-column>
        <el-table-column align="right" label="Linhas (aprox.)" width="140">
          <template #default="{ row }">{{ ['arquivos', 'logs-arquivo'].includes(row.chave) ? '—' : row.linhas.toLocaleString('pt-BR') }}</template>
        </el-table-column>
        <el-table-column align="right" label="Por dia" width="120">
          <template #default="{ row }">{{ temHistorico ? tamanho(row.crescimentoPorDia) : '—' }}</template>
        </el-table-column>
        <el-table-column label="Fica guardado" min-width="240" prop="guarda" />
      </el-table>
    </ContentWrap>

    <ContentWrap title="Retenção e limites">
      <p class="ajuda mb-12px">
        A limpeza roda de madrugada. Reduzir um prazo apaga os dados mais antigos na próxima execução, sem como recuperar.
        Chamados, mensagens do chat, inventário e a trilha de auditoria não entram aqui (os anexos do chat, sim).
      </p>
      <el-table :data="itens">
        <el-table-column label="Item" min-width="300">
          <template #default="{ row }">
            <div>{{ row.rotulo }}</div>
            <div v-if="row.ajuda" class="ajuda">{{ row.ajuda }}</div>
          </template>
        </el-table-column>
        <el-table-column label="Valor" width="230">
          <template #default="{ row }">
            <el-input-number
              v-model="edicao[row.chave]"
              :disabled="!podeAlterar"
              :min="row.semLimite ? 0 : row.minimo"
              :precision="0"
              controls-position="right"
              size="small"
            />
            <span class="ml-8px">{{ row.unidade }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Regra" min-width="210">
          <template #default="{ row }">
            <span class="ajuda">
              Padrão {{ row.padrao }}; mínimo {{ row.minimo }}{{ row.semLimite ? `; 0 = ${row.zero || 'sem limite'}` : '' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column v-if="podeAlterar" align="center" label="" width="100">
          <template #default="{ row }">
            <el-button :disabled="edicao[row.chave] === row.valor" link type="primary" @click="salvar(row)">Salvar</el-button>
          </template>
        </el-table-column>
      </el-table>
    </ContentWrap>
  </div>
</template>

<script lang="ts" setup>
import * as Infra from '@/api/infra/infraestrutura'
import { checkPermi } from '@/utils/permission'

defineOptions({ name: 'InfraGeral' })

const message = useMessage()
const carregando = ref(false)
const c = ref<Infra.Capacidade>()
const itens = ref<Infra.ItemRetencao[]>([])
const edicao = reactive<Record<string, number>>({})
const podeAlterar = checkPermi(['infra:retencao:update'])

const temHistorico = computed(() => !!c.value && c.value.diasDeHistorico >= c.value.diasMinimos)
const textoHistorico = computed(() =>
  temHistorico.value
    ? `média de ${c.value!.diasDeHistorico} dias`
    : `aparece com ${c.value?.diasMinimos} dias de medição (hoje: ${c.value?.diasDeHistorico})`
)
const previsao = computed(() => {
  const d = c.value?.diasAteEncher
  if (d == null) return '—'
  return d > 3650 ? 'Mais de 10 anos' : `${d.toLocaleString('pt-BR')} dias`
})
const areasOrdenadas = computed(() => [...(c.value?.areas || [])].sort((a, b) => b.bytes - a.bytes))

const tamanho = (bytes: number) => {
  const sinal = bytes < 0 ? '-' : ''
  let v = Math.abs(bytes)
  for (const u of ['B', 'KB', 'MB', 'GB']) {
    if (v < 1024) return `${sinal}${u === 'B' ? v : v.toFixed(1).replace('.', ',')} ${u}`
    v /= 1024
  }
  return `${sinal}${v.toFixed(1).replace('.', ',')} TB`
}

const carregar = async () => {
  carregando.value = true
  try {
    const [capacidade, retencao] = await Promise.all([Infra.getCapacidade(), Infra.getRetencao()])
    c.value = capacidade
    itens.value = retencao
    retencao.forEach((i) => (edicao[i.chave] = i.valor))
  } finally {
    carregando.value = false
  }
}

const salvar = async (i: Infra.ItemRetencao) => {
  const novo = edicao[i.chave]
  if (novo !== 0 && novo < i.valor && ['dias', 'horas'].includes(i.unidade)) {
    await message.confirm(`Reduzir "${i.rotulo}" de ${i.valor} para ${novo} ${i.unidade} apaga os dados mais antigos na próxima limpeza. Continuar?`)
  }
  await Infra.salvarRetencao(i.chave, novo)
  message.success('Salvo')
  await carregar()
}

onMounted(carregar)
</script>

<style lang="scss" scoped>
.numeros {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 16px;
}

.rotulo {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.valor {
  margin: 4px 0;
  font-size: 26px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.ajuda {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
