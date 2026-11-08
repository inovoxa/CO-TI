<template>
  <ContentWrap>
    <div class="topo">
      <p class="ajuda">
        Rotinas que o CO-TI roda sozinho. A lista é atualizada a cada minuto e cada rotina aparece depois da primeira execução.
      </p>
      <el-button :loading="carregando" @click="carregar">
        <Icon class="mr-5px" icon="ep:refresh" /> Atualizar
      </el-button>
    </div>
    <el-table v-loading="carregando" :data="rotinas" :stripe="true">
      <el-table-column label="Situação" width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 'erro' ? 'danger' : 'success'" disable-transitions>
            {{ row.status === 'erro' ? 'Com erro' : 'Normal' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Rotina" min-width="280" prop="rotulo" />
      <el-table-column label="Última execução" width="170">
        <template #default="{ row }">{{ dataHora(row.ultimaExecucao) }}</template>
      </el-table-column>
      <el-table-column align="right" label="Duração" width="100">
        <template #default="{ row }">{{ duracao(row.duracaoMs) }}</template>
      </el-table-column>
      <el-table-column align="right" label="Execuções" width="110">
        <template #default="{ row }">{{ row.execucoes.toLocaleString('pt-BR') }}</template>
      </el-table-column>
      <el-table-column align="right" label="Falhas" width="90">
        <template #default="{ row }">{{ row.falhas.toLocaleString('pt-BR') }}</template>
      </el-table-column>
      <el-table-column label="Último erro" min-width="280">
        <template #default="{ row }">
          <template v-if="row.ultimoErro">
            <div class="ajuda">{{ dataHora(row.ultimaFalha) }}</div>
            <div class="erro">{{ row.ultimoErro }}</div>
          </template>
          <span v-else class="ajuda">—</span>
        </template>
      </el-table-column>
    </el-table>
  </ContentWrap>
</template>

<script lang="ts" setup>
import * as Infra from '@/api/infra/infraestrutura'

defineOptions({ name: 'InfraRotinas' })

const carregando = ref(false)
const rotinas = ref<Infra.Rotina[]>([])

const dataHora = (d: string | number | null) => (d ? new Date(d).toLocaleString('pt-BR') : '—')
const duracao = (ms: number) => (ms < 1000 ? `${ms} ms` : `${(ms / 1000).toFixed(1).replace('.', ',')} s`)

const carregar = async () => {
  carregando.value = true
  try {
    rotinas.value = await Infra.getRotinas()
  } finally {
    carregando.value = false
  }
}

onMounted(carregar)
</script>

<style lang="scss" scoped>
.topo {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.ajuda {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.erro {
  font-size: 12px;
  word-break: break-word;
  white-space: pre-wrap;
}
</style>
