<!-- Ativos cuja unidade ou usuário não batem com as regras (faixa de IP da unidade; login do agente), e a correção. -->
<template>
  <Dialog v-model="visivel" title="Inconsistências do inventário" width="980px">
    <div v-loading="carregando">
      <template v-if="r">
        <div class="mb-10px flex flex-wrap gap-8px">
          <el-tag disable-transitions type="info">{{ r.ativos }} ativos verificados</el-tag>
          <el-tag :type="r.unidadeErrada ? 'warning' : 'success'" disable-transitions>
            {{ r.unidadeErrada }} com unidade diferente da faixa de IP
          </el-tag>
          <el-tag :type="r.usuarioErrado ? 'warning' : 'success'" disable-transitions>
            {{ r.usuarioErrado }} com usuário diferente do login
          </el-tag>
          <el-tag disable-transitions type="info">{{ r.semFaixa }} sem nenhum IP numa faixa</el-tag>
        </div>
        <div class="dica mb-10px">
          Unidade = a escolhida à mão; senão a da faixa de IP da unidade (Monitoramento → Unidades e
          endereços) do primeiro IP da máquina que cai numa faixa; senão nenhuma. Usuário = a pessoa
          do portal dona do login que o agente informou.
        </div>
        <el-table :data="r.itens" max-height="380" size="small">
          <el-table-column label="Ativo" min-width="150" prop="nome" show-overflow-tooltip />
          <el-table-column label="Campo" width="85">
            <template #default="{ row }">{{ row.tipo === 'unidade' ? 'Unidade' : 'Usuário' }}</template>
          </el-table-column>
          <el-table-column label="Hoje" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ row.atual || '—' }}</template>
          </el-table-column>
          <el-table-column label="Correto" min-width="150" show-overflow-tooltip>
            <template #default="{ row }"
              ><b>{{ row.correto || '(vazio)' }}</b></template
            >
          </el-table-column>
          <el-table-column label="Motivo" min-width="230" prop="motivo" show-overflow-tooltip />
          <el-table-column label="IPs" min-width="140" prop="ips" show-overflow-tooltip />
        </el-table>
        <div v-if="r.itens.length < r.unidadeErrada + r.usuarioErrado" class="dica mt-4px">
          Mostrando {{ r.itens.length }} de {{ r.unidadeErrada + r.usuarioErrado }}; a correção vale
          para todos.
        </div>
        <template v-if="r.redesSemFaixa.length">
          <div class="mt-12px font-bold">Redes sem faixa cadastrada</div>
          <div class="dica mb-4px">
            Cadastre a faixa na unidade (Monitoramento → Unidades e endereços) e as máquinas dessas
            redes passam a ter unidade.
          </div>
          <el-tag
            v-for="x in r.redesSemFaixa"
            :key="x.rede"
            class="mb-4px mr-6px"
            disable-transitions
            type="info"
            >{{ x.rede }} · {{ x.maquinas }} máquina(s)</el-tag
          >
        </template>
      </template>
    </div>
    <template #footer>
      <el-button @click="carregar">Verificar de novo</el-button>
      <el-button
        v-hasPermi="['inventario:ativo:update']"
        :disabled="!r || !r.unidadeErrada"
        :loading="corrigindo"
        @click="corrigir(['unidade'])"
        >Corrigir unidades ({{ r?.unidadeErrada ?? 0 }})</el-button
      >
      <el-button
        v-hasPermi="['inventario:ativo:update']"
        :disabled="!r || !r.usuarioErrado"
        :loading="corrigindo"
        @click="corrigir(['usuario'])"
        >Corrigir usuários ({{ r?.usuarioErrado ?? 0 }})</el-button
      >
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Inv from '@/api/inventario'

const emit = defineEmits<{ corrigido: [] }>()
const message = useMessage()
const visivel = ref(false)
const carregando = ref(false)
const corrigindo = ref(false)
const r = ref<Inv.Inconsistencias>()

const carregar = async () => {
  carregando.value = true
  try {
    r.value = await Inv.getInconsistencias()
  } finally {
    carregando.value = false
  }
}

const abrir = () => {
  visivel.value = true
  carregar()
}

const corrigir = async (tipos: string[]) => {
  const n = tipos[0] === 'unidade' ? r.value!.unidadeErrada : r.value!.usuarioErrado
  await message.confirm(
    `Corrigir ${n} ativo(s)? Cada mudança fica no histórico do ativo. Ativos com unidade escolhida à mão não mudam.`
  )
  corrigindo.value = true
  try {
    const x = await Inv.corrigirInconsistencias(tipos)
    message.success(`${(x.unidade ?? 0) + (x.usuario ?? 0)} ativo(s) corrigido(s)`)
    emit('corrigido')
    await carregar()
  } finally {
    corrigindo.value = false
  }
}

defineExpose({ abrir })
</script>

<style scoped>
.dica {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
