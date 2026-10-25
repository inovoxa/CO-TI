<template>
  <div v-loading="carregando">
    <template v-if="c">
      <el-alert
        :closable="false"
        :type="c.excedida ? 'error' : c.softwares.length ? 'success' : 'info'"
        class="mb-10px"
        show-icon
      >
        <template #title>
          <template v-if="!c.softwares.length">
            Diga quais softwares do inventário contam como uso desta licença.
          </template>
          <template v-else>
            {{ c.instalacoes }} máquina(s) com o software ·
            {{ c.quantidade < 0 ? 'licença ilimitada' : `${c.quantidade} comprada(s)` }} ·
            {{ c.emUso }} uso(s) registrado(s)
            <template v-if="c.excedida"> — instalado em mais máquinas que o comprado</template>
          </template>
        </template>
      </el-alert>

      <div class="mb-6px text-13px font-bold">Softwares que contam</div>
      <div class="mb-10px flex flex-wrap items-center gap-6px">
        <el-tag
          v-for="s in c.softwares"
          :key="s.id"
          :closable="editavel"
          disable-transitions
          @close="desvincular(s)"
          >{{ s.nome }}<span v-if="s.editora"> · {{ s.editora }}</span> ({{
            s.instalacoes
          }})</el-tag
        >
        <template v-if="editavel">
          <el-select
            v-model="escolhido"
            :loading="buscando"
            :remote-method="buscar"
            class="!w-280px"
            filterable
            placeholder="Procurar no catálogo de softwares"
            remote
            size="small"
          >
            <el-option
              v-for="o in opcoes"
              :key="o.id"
              :label="`${o.nome}${o.editora ? ' · ' + o.editora : ''} (${o.instalacoes})`"
              :value="o.id"
            />
          </el-select>
          <el-button :disabled="!escolhido" size="small" type="primary" @click="vincular"
            >Incluir</el-button
          >
        </template>
      </div>

      <el-table v-if="c.ativos.length" :data="c.ativos" max-height="380" size="small">
        <el-table-column label="Máquina" min-width="160">
          <template #default="{ row }">
            <el-link :href="`/inventario/ativos?id=${row.id}`" target="_blank" type="primary">{{
              row.nome
            }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="Usuário" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.usuarioNome || row.usuarioLogin || '—' }}</template>
        </el-table-column>
        <el-table-column label="Unidade" min-width="140" prop="deptNome" show-overflow-tooltip />
      </el-table>
    </template>
  </div>
</template>

<script lang="ts" setup>
import * as Inv from '@/api/inventario'

const props = defineProps<{ licencaId: number; editavel: boolean }>()
const emit = defineEmits(['alterado'])

const c = ref<Inv.Conformidade>()
const carregando = ref(false)
const escolhido = ref<number>()
const opcoes = ref<Inv.Software[]>([])
const buscando = ref(false)

const carregar = async () => {
  carregando.value = true
  try {
    c.value = await Inv.getConformidade(props.licencaId)
  } finally {
    carregando.value = false
  }
}

const buscar = async (texto: string) => {
  if (!texto) return
  buscando.value = true
  try {
    opcoes.value = (await Inv.getSoftwares({ busca: texto, pageNo: 1, pageSize: 20 })).list
  } finally {
    buscando.value = false
  }
}

const vincular = async () => {
  await Inv.vincularSoftwareLicenca(props.licencaId, escolhido.value!)
  escolhido.value = undefined
  await carregar()
  emit('alterado')
}

const desvincular = async (s: Inv.Software) => {
  await Inv.desvincularSoftwareLicenca(props.licencaId, s.id)
  await carregar()
  emit('alterado')
}

watch(() => props.licencaId, carregar, { immediate: true })
</script>
