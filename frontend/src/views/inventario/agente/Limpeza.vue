<!-- Limpeza do inventário: máquinas repetidas (formatadas, trocadas, nome reaproveitado) e máquinas paradas. -->
<template>
  <div>
    <ContentWrap title="Máquinas repetidas">
      <p class="ajuda mt-0">
        Máquina formatada com o mesmo hardware já volta no mesmo ativo. Aqui aparecem as que não deu
        para reconhecer sozinho: mesmo nome, série, UUID ou MAC em mais de um ativo (placa-mãe
        trocada, cadastro manual antigo, nome reaproveitado num equipamento novo). Para cada grupo,
        escolha o que <b>fica</b> e:
      </p>
      <ul class="ajuda mt-0">
        <li
          ><b>Juntar</b> — é a mesma máquina: chamados, patrimônio e histórico passam para o que
          fica.</li
        >
        <li
          ><b>Dar baixa nos outros</b> — o nome foi para outro equipamento: os antigos ficam como
          "baixado".</li
        >
        <li><b>São diferentes</b> — o grupo não aparece mais.</li>
      </ul>
      <el-button :loading="carregandoRepetidas" class="mb-10px" @click="carregarRepetidas">
        <Icon class="mr-4px" icon="ep:refresh" /> Procurar de novo
      </el-button>
      <el-empty
        v-if="!carregandoRepetidas && !grupos.length"
        :image-size="60"
        description="Nenhuma máquina repetida."
      />
      <div v-for="(g, i) in grupos" :key="g.ativos.map((a) => a.id).join('-')" class="grupo">
        <div class="mb-6px flex flex-wrap items-center gap-6px">
          <el-tag v-for="m in g.motivos" :key="m" disable-transitions size="small" type="warning">{{
            m
          }}</el-tag>
        </div>
        <el-table :data="g.ativos" size="small">
          <el-table-column label="Fica" width="60">
            <template #default="{ row }">
              <el-radio v-model="fica[i]" :value="row.id"><span></span></el-radio>
            </template>
          </el-table-column>
          <el-table-column label="Nome" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">
              <el-link :underline="false" type="primary" @click="abrir(row.id)">{{
                row.nome
              }}</el-link>
            </template>
          </el-table-column>
          <el-table-column label="Origem" width="120">
            <template #default="{ row }">{{ Inv.ORIGENS[row.origem] ?? row.origem }}</template>
          </el-table-column>
          <el-table-column label="Série / modelo" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{
              [row.numeroSerie, row.modelo].filter(Boolean).join(' · ') || '—'
            }}</template>
          </el-table-column>
          <el-table-column label="MAC / IP" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{
              [row.mac, row.ip].filter(Boolean).join(' · ') || '—'
            }}</template>
          </el-table-column>
          <el-table-column label="Usuário" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ row.usuarioNome || row.usuarioLogin || '—' }}</template>
          </el-table-column>
          <el-table-column label="Patrimônio" prop="patrimonio" width="100" />
          <el-table-column align="right" label="Chamados" width="85">
            <template #default="{ $index }">{{ g.chamados[$index] }}</template>
          </el-table-column>
          <el-table-column label="Último contato" width="130">
            <template #default="{ row }">
              <span :class="{ parada: row.semContato }">{{ quando(row.ultimoContato) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <div class="mt-8px flex flex-wrap gap-8px">
          <el-button size="small" type="primary" @click="juntar(g, i)"
            >Juntar no escolhido</el-button
          >
          <el-button size="small" @click="baixarOutros(g, i)">Dar baixa nos outros</el-button>
          <el-button size="small" text @click="diferentes(g)">São diferentes</el-button>
        </div>
      </div>
    </ContentWrap>

    <ContentWrap title="Máquinas paradas">
      <p class="ajuda mt-0">
        Máquinas de agente ou SNMP sem contato há muito tempo. Dar baixa tira das contas e dos
        alertas; se a máquina voltar a mandar inventário, ela volta para "em uso" sozinha. Para a
        baixa ser automática, defina
        <code>CO_TI_INVENTARIO_BAIXA_DIAS</code> no servidor.
      </p>
      <el-form :inline="true" class="-mb-15px" label-width="0">
        <el-form-item>
          <el-select v-model="dias" class="!w-200px" @change="carregarParadas">
            <el-option
              v-for="d in [30, 60, 90, 180, 365]"
              :key="d"
              :label="`Sem contato há ${d} dias`"
              :value="d"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            :disabled="!selecionadas.length"
            type="danger"
            plain
            @click="baixarSelecionadas"
          >
            Dar baixa nas {{ selecionadas.length || '' }} selecionadas
          </el-button>
        </el-form-item>
      </el-form>
      <el-table
        v-loading="carregandoParadas"
        :data="paradas"
        class="mt-10px"
        row-key="id"
        size="small"
        @selection-change="(l: Inv.Ativo[]) => (selecionadas = l)"
      >
        <el-table-column type="selection" width="40" />
        <el-table-column label="Nome" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link :underline="false" type="primary" @click="abrir(row.id)">{{
              row.nome
            }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="Unidade" min-width="150" prop="deptNome" show-overflow-tooltip />
        <el-table-column label="Usuário" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.usuarioNome || row.usuarioLogin || '—' }}</template>
        </el-table-column>
        <el-table-column label="Situação" width="110">
          <template #default="{ row }">{{
            Inv.rotulo(Inv.SITUACOES_ATIVO, row.situacao)
          }}</template>
        </el-table-column>
        <el-table-column label="Patrimônio" prop="patrimonio" width="110" />
        <el-table-column label="Último contato" width="140">
          <template #default="{ row }">{{ quando(row.ultimoContato) }}</template>
        </el-table-column>
      </el-table>
      <div v-if="paradas.length >= 2000" class="ajuda mt-6px">Mostrando as 2000 mais antigas.</div>
    </ContentWrap>
  </div>
</template>

<script lang="ts" setup>
import * as Inv from '@/api/inventario'

defineOptions({ name: 'InventarioLimpeza' })
const message = useMessage()
const router = useRouter()

const grupos = ref<Inv.GrupoRepetido[]>([])
const fica = ref<number[]>([])
const carregandoRepetidas = ref(false)
const dias = ref(90)
const paradas = ref<Inv.Ativo[]>([])
const selecionadas = ref<Inv.Ativo[]>([])
const carregandoParadas = ref(false)

const quando = (d?: string) =>
  d ? new Date(d).toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' }) : '—'
const abrir = (id: number) => router.push({ path: '/inventario/ativos', query: { id } })

const carregarRepetidas = async () => {
  carregandoRepetidas.value = true
  try {
    grupos.value = await Inv.getRepetidas()
    fica.value = grupos.value.map((g) => g.ativos[0].id)
  } finally {
    carregandoRepetidas.value = false
  }
}
const carregarParadas = async () => {
  carregandoParadas.value = true
  try {
    paradas.value = await Inv.getParadas(dias.value)
    selecionadas.value = []
  } finally {
    carregandoParadas.value = false
  }
}

const outros = (g: Inv.GrupoRepetido, i: number) => g.ativos.filter((a) => a.id !== fica.value[i])
const nomeDe = (g: Inv.GrupoRepetido, i: number) =>
  g.ativos.find((a) => a.id === fica.value[i])?.nome

const juntar = async (g: Inv.GrupoRepetido, i: number) => {
  const l = outros(g, i)
  try {
    await message.confirm(
      `Juntar ${l.map((a) => a.nome).join(', ')} em "${nomeDe(g, i)}"? Os outros saem das listas; chamados, ` +
        'patrimônio e histórico passam para o que fica. Se o agente está mandando por um dos outros, ele passa a ' +
        'atualizar o que fica.'
    )
  } catch {
    return
  }
  await Inv.mesclarAtivos(
    fica.value[i],
    l.map((a) => a.id)
  )
  message.success('Ativos juntados')
  await carregarRepetidas()
}
const baixarOutros = async (g: Inv.GrupoRepetido, i: number) => {
  const l = outros(g, i)
  try {
    await message.confirm(
      `Dar baixa em ${l.map((a) => a.nome).join(', ')}? "${nomeDe(g, i)}" continua como está.`
    )
  } catch {
    return
  }
  await Inv.baixarAtivos(l.map((a) => a.id))
  message.success('Baixa registrada')
  await Promise.all([carregarRepetidas(), carregarParadas()])
}
const diferentes = async (g: Inv.GrupoRepetido) => {
  await Inv.marcarDistintos(g.ativos.map((a) => a.id))
  message.success('Não aparecem mais como repetidas')
  await carregarRepetidas()
}
const baixarSelecionadas = async () => {
  try {
    await message.confirm(`Dar baixa em ${selecionadas.value.length} máquina(s) paradas?`)
  } catch {
    return
  }
  const n = await Inv.baixarAtivos(selecionadas.value.map((a) => a.id))
  message.success(`${n} máquina(s) baixadas`)
  await Promise.all([carregarParadas(), carregarRepetidas()])
}

onMounted(() => Promise.all([carregarRepetidas(), carregarParadas()]))
</script>

<style lang="scss" scoped>
.ajuda {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.grupo {
  padding: 10px 12px;
  margin-bottom: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.parada {
  color: var(--el-color-danger);
}
</style>
