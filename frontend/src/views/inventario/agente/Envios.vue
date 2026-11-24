<!-- Cada pedido dos agentes (aceito ou recusado): quem chegou, quem foi recusado e por quê. -->
<template>
  <div>
    <el-row v-if="resumo" :gutter="12">
      <el-col :lg="6" :sm="12" :xs="24">
        <ContentWrap>
          <div class="rotulo">Máquinas que falaram (24 h)</div>
          <div class="numero">{{ resumo.maquinas }}</div>
        </ContentWrap>
      </el-col>
      <el-col :lg="6" :sm="12" :xs="24">
        <ContentWrap>
          <div class="rotulo">Inventários gravados (24 h)</div>
          <div class="numero">{{ resumo.porResultado.gravado ?? 0 }}</div>
        </ContentWrap>
      </el-col>
      <el-col :lg="6" :sm="12" :xs="24">
        <ContentWrap>
          <div class="rotulo">Recusados (24 h)</div>
          <el-link
            :type="recusados ? 'danger' : 'default'"
            :underline="false"
            class="!text-28px font-bold"
            @click="filtrar('recusados')"
            >{{ recusados }}</el-link
          >
        </ContentWrap>
      </el-col>
      <el-col :lg="6" :sm="12" :xs="24">
        <ContentWrap>
          <div class="rotulo">Erros ao gravar (24 h)</div>
          <el-link
            :type="resumo.porResultado.erro ? 'danger' : 'default'"
            :underline="false"
            class="!text-28px font-bold"
            @click="filtrar('erro')"
            >{{ resumo.porResultado.erro ?? 0 }}</el-link
          >
        </ContentWrap>
      </el-col>
    </el-row>

    <ContentWrap v-if="resumo?.recusas.length" title="IPs recusados nas últimas 24 h">
      <el-table :data="resumo.recusas" size="small">
        <el-table-column label="IP" prop="ip" width="160" />
        <el-table-column label="Motivo" min-width="200">
          <template #default="{ row }">
            <el-tag
              :type="Inv.RESULTADOS_ENVIO[row.resultado]?.cor"
              disable-transitions
              size="small"
            >
              {{ Inv.RESULTADOS_ENVIO[row.resultado]?.rotulo ?? row.resultado }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column align="right" label="Registros" prop="vezes" width="100" />
        <el-table-column label="Último" width="150">
          <template #default="{ row }">{{ quando(row.ultima) }}</template>
        </el-table-column>
      </el-table>
      <div class="ajuda mt-8px">
        <b>Recusado: rede</b> — o IP não está em <code>CO_TI_INVENTARIO_REDES</code>.
        <b>Recusado: senha</b> — o agente não mandou (ou errou) o <code>/USER</code> e o
        <code>/PWD</code>. Recusas repetidas do mesmo IP entram uma vez por minuto.
      </div>
    </ContentWrap>

    <ContentWrap>
      <el-form :inline="true" class="-mb-15px" label-width="0">
        <el-form-item>
          <el-select
            v-model="q.resultado"
            class="!w-200px"
            clearable
            placeholder="Resultado"
            @change="buscar"
          >
            <el-option label="Com problema" value="problemas" />
            <el-option label="Recusados" value="recusados" />
            <el-option label="Inventários (gravados e erros)" value="inventarios" />
            <el-option
              v-for="(r, k) in Inv.RESULTADOS_ENVIO"
              :key="k"
              :label="r.rotulo"
              :value="k"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="q.horas" class="!w-150px" @change="buscar">
            <el-option :value="1" label="Última hora" />
            <el-option :value="24" label="Últimas 24 h" />
            <el-option :value="168" label="Últimos 7 dias" />
            <el-option :value="0" :label="`Tudo (${resumo?.dias ?? 30} dias)`" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="q.busca"
            class="!w-240px"
            clearable
            placeholder="Máquina, IP ou DEVICEID"
            @keyup.enter="buscar"
          >
            <template #prefix><Icon icon="ep:search" /></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-button @click="atualizar"
            ><Icon class="mr-4px" icon="ep:refresh" /> Atualizar</el-button
          >
          <el-button v-hasPermi="['inventario:ativo:exportar']" plain @click="exportar">
            <Icon class="mr-4px" icon="ep:download" /> Exportar
          </el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-table v-loading="loading" :data="lista" size="small">
        <el-table-column label="Quando" width="140">
          <template #default="{ row }">{{ quando(row.quando) }}</template>
        </el-table-column>
        <el-table-column label="Máquina" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link
              v-if="row.ativoId && !row.ativoRemovido"
              :underline="false"
              type="primary"
              @click="router.push({ path: '/inventario/ativos', query: { id: row.ativoId } })"
              >{{ row.ativoNome }}</el-link
            >
            <span v-else-if="row.ativoNome">{{ row.ativoNome }} (juntado/removido)</span>
            <span v-else class="ajuda">{{ row.deviceId || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="IP" prop="ip" width="130" />
        <el-table-column label="Resultado" width="170">
          <template #default="{ row }">
            <el-tag
              :type="Inv.RESULTADOS_ENVIO[row.resultado]?.cor"
              disable-transitions
              size="small"
            >
              {{ Inv.RESULTADOS_ENVIO[row.resultado]?.rotulo ?? row.resultado }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Detalhe" min-width="220" prop="detalhe" show-overflow-tooltip />
        <el-table-column label="Agente" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.agente || '—'
            }}<span v-if="row.consulta" class="ajuda"> · {{ row.consulta }}</span>
          </template>
        </el-table-column>
        <el-table-column align="right" label="Tamanho" width="90">
          <template #default="{ row }">{{ tamanho(row.bytes) }}</template>
        </el-table-column>
        <el-table-column align="right" label="Tempo" width="80">
          <template #default="{ row }">{{ row.ms == null ? '—' : `${row.ms} ms` }}</template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:limit="q.pageSize"
        v-model:page="q.pageNo"
        :total="total"
        @pagination="carregar"
      />
    </ContentWrap>
  </div>
</template>

<script lang="ts" setup>
import * as Inv from '@/api/inventario'
import download from '@/utils/download'

defineOptions({ name: 'InventarioEnvios' })
const router = useRouter()
const loading = ref(false)
const lista = ref<Inv.Envio[]>([])
const total = ref(0)
const resumo = ref<Inv.ResumoEnvios>()
const q = reactive({
  pageNo: 1,
  pageSize: 30,
  resultado: undefined as string | undefined,
  horas: 24,
  busca: ''
})

const recusados = computed(() =>
  ['recusado_rede', 'recusado_senha', 'invalido'].reduce(
    (n, k) => n + (resumo.value?.porResultado[k] ?? 0),
    0
  )
)
const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'medium' }) : '—'
const tamanho = (b?: number) =>
  b == null
    ? '—'
    : b < 1024
      ? `${b} B`
      : b < 1048576
        ? `${Math.round(b / 1024)} KB`
        : `${(b / 1048576).toFixed(1)} MB`

const params = () => {
  const p: any = { ...q }
  for (const k of Object.keys(p)) if (p[k] === '' || p[k] == null) delete p[k]
  return p
}
const carregar = async () => {
  loading.value = true
  try {
    const r = await Inv.getEnvios(params())
    lista.value = r.list
    total.value = r.total
  } finally {
    loading.value = false
  }
}
const buscar = () => {
  q.pageNo = 1
  carregar()
}
const filtrar = (resultado: string) => {
  q.resultado = resultado
  q.horas = 24
  buscar()
}
const atualizar = async () => {
  resumo.value = await Inv.getResumoEnvios()
  await carregar()
}
const exportar = async () =>
  download.excel(await Inv.exportarEnvios(params()), 'envios-agentes.xlsx')

defineExpose({ atualizar })
onMounted(atualizar)
</script>

<style lang="scss" scoped>
.rotulo,
.ajuda {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.rotulo {
  font-size: 13px;
}

.numero {
  font-size: 28px;
  font-weight: bold;
}
</style>
