<!-- Aviso ao entrar no sistema: os recados de hoje (calendário) que a pessoa ainda não deu "Ciente".
     Fechar no X não marca: o aviso volta na próxima entrada. -->
<template>
  <el-dialog
    v-model="visivel"
    :close-on-click-modal="false"
    append-to-body
    class="recados-ao-entrar"
    title="Recados"
    width="560px"
  >
    <div class="lista">
      <div
        v-for="r in recados"
        :key="r.id"
        :style="{ borderLeftColor: r.cor || 'var(--el-color-primary)' }"
        class="recado"
      >
        <div class="titulo">📢 {{ r.titulo }}</div>
        <div class="texto">{{ r.texto }}</div>
        <div class="rodape">
          {{ periodo(r) }} ·
          {{ r.unidades.length ? r.unidades.map((u) => u.nome).join(', ') : 'Recado geral' }}
          <span v-if="r.autor"> · {{ r.autor }}</span>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="visivel = false">Ver depois</el-button>
      <el-button :loading="marcando" type="primary" @click="ciente">Ciente</el-button>
    </template>
  </el-dialog>
</template>

<script lang="ts" setup>
import * as Api from '@/api/calendario'

defineOptions({ name: 'RecadosAoEntrar' })

const visivel = ref(false)
const marcando = ref(false)
const recados = ref<Api.RecadoVO[]>([])

const br = (d: string) => `${d.slice(8)}/${d.slice(5, 7)}`
const periodo = (r: Api.RecadoVO) =>
  r.inicio === r.fim ? br(r.inicio) : `${br(r.inicio)} a ${br(r.fim)}`

const ciente = async () => {
  marcando.value = true
  try {
    await Api.recadosCiente(recados.value.map((r) => r.id))
    visivel.value = false
  } finally {
    marcando.value = false
  }
}

onMounted(async () => {
  try {
    recados.value = await Api.getRecadosPendentes()
    visivel.value = recados.value.length > 0
  } catch {
    // sem recado, sem aviso: não atrapalha a entrada
  }
})
</script>

<style lang="scss" scoped>
.lista {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 60vh;
  overflow-y: auto;
}

.recado {
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-left: 4px solid;
  border-radius: 4px;
}

.titulo {
  margin-bottom: 4px;
  font-weight: 600;
}

.texto {
  line-height: 1.5;
  white-space: pre-wrap;
}

.rodape {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
