<!-- Importar planilha com prévia: analisa sem gravar, mostra o que será adicionado, atualizado e removido, e só então aplica -->
<template>
  <input ref="entrada" accept=".xlsx" class="hidden" type="file" @change="escolhido" />
  <Dialog v-model="visivel" :title="`Importar ${nomePlural}`" width="820px">
    <template v-if="previa">
      <p class="mt-0 text-13px">
        <b>{{ arquivo?.name }}</b> foi analisado e <b>nada foi gravado ainda</b>. Confira o que vai
        mudar antes de aplicar.
      </p>

      <div class="mb-12px flex flex-col gap-6px">
        <el-alert
          v-if="previa.novos.length"
          :closable="false"
          :title="`${previa.novos.length} ${rotulo(previa.novos.length)} novo(s) será(ão) adicionado(s).`"
          show-icon
          type="success"
        />
        <el-alert
          v-if="previa.alterados.length"
          :closable="false"
          :title="`${previa.alterados.length} ${rotulo(previa.alterados.length)} será(ão) atualizado(s).`"
          show-icon
          type="info"
        />
        <el-alert
          v-if="previa.departamentos?.length"
          :closable="false"
          :title="`${previa.departamentos.length} departamento(s) não existe(m) nesta instalação e será(ão) criado(s).`"
          show-icon
          type="info"
        />
        <el-alert
          v-if="dadosRemovidos"
          :closable="false"
          :title="`${dadosRemovidos} dado(s) será(ão) removido(s): as células estão em branco no arquivo e os campos ficarão vazios.`"
          show-icon
          type="warning"
        />
        <el-alert
          v-if="previa.ausentes.length"
          :closable="false"
          :title="
            remover
              ? `${previa.ausentes.length} ${rotulo(previa.ausentes.length)} será(ão) removido(s) do sistema.`
              : `${previa.ausentes.length} ${rotulo(previa.ausentes.length)} não está(ão) no arquivo e será(ão) mantido(s).`
          "
          :type="remover ? 'error' : 'info'"
          show-icon
        />
        <el-alert
          v-if="previa.recusados"
          :closable="false"
          :title="`${previa.recusados} linha(s) com problema não será(ão) importada(s).`"
          show-icon
          type="error"
        />
        <el-alert
          v-if="nadaAFazer && !previa.recusados"
          :closable="false"
          show-icon
          title="O arquivo é igual ao que está cadastrado: não há nada para aplicar."
          type="success"
        />
      </div>
      <p v-if="previa.semMudanca" class="nota">
        {{ previa.semMudanca }} {{ rotulo(previa.semMudanca) }} sem mudança.
      </p>
      <p
        v-if="previa.recusados && previa.erros.some((e) => e.includes('não encontrada'))"
        class="nota"
      >
        Unidade não encontrada: importe primeiro a planilha de unidades (Monitoramento → Unidades e
        endereços) e depois a de monitores.
      </p>

      <el-collapse v-model="abertos">
        <el-collapse-item
          v-if="previa.novos.length"
          :title="`Serão adicionados (${previa.novos.length})`"
          name="novos"
        >
          <ul class="lista">
            <li v-for="(i, n) in previa.novos" :key="n">
              <b>{{ i.nome }}</b>
              <div class="nota">{{ resumo(i) }}</div>
            </li>
          </ul>
        </el-collapse-item>
        <el-collapse-item
          v-if="previa.alterados.length"
          :title="`Serão atualizados (${previa.alterados.length})`"
          name="alterados"
        >
          <ul class="lista">
            <li v-for="i in previa.alterados" :key="i.id">
              <b>{{ i.nome }}</b>
              <div v-for="m in i.mudancas" :key="m.campo" class="text-13px">
                <span class="nota">{{ m.campo }}: </span>
                <template v-if="!m.de">
                  <span class="adicionado">{{ m.para }}</span>
                  <el-tag class="ml-4px" disable-transitions size="small" type="success"
                    >adicionado</el-tag
                  >
                </template>
                <template v-else-if="!m.para">
                  <span class="removido">{{ m.de }}</span>
                  <el-tag class="ml-4px" disable-transitions size="small" type="danger"
                    >removido</el-tag
                  >
                </template>
                <template v-else>{{ m.de }} → {{ m.para }}</template>
              </div>
            </li>
          </ul>
        </el-collapse-item>
        <el-collapse-item
          v-if="previa.departamentos?.length"
          :title="`Departamentos que serão criados (${previa.departamentos.length})`"
          name="departamentos"
        >
          <ul class="lista">
            <li v-for="d in previa.departamentos" :key="d">{{ d }}</li>
          </ul>
        </el-collapse-item>
        <el-collapse-item
          v-if="previa.ausentes.length"
          :title="`Não estão no arquivo (${previa.ausentes.length})`"
          name="ausentes"
        >
          <el-checkbox v-model="remover" class="mb-8px">
            Remover do sistema os {{ previa.ausentes.length }} que não estão no arquivo
          </el-checkbox>
          <ul class="lista">
            <li v-for="i in previa.ausentes" :key="i.id">
              <span :class="{ removido: remover }">{{ i.nome }}</span>
              <div v-if="i.aviso" class="nota">{{ i.aviso }}</div>
            </li>
          </ul>
        </el-collapse-item>
        <el-collapse-item
          v-if="previa.erros.length"
          :title="`Linhas com problema (${previa.recusados})`"
          name="erros"
        >
          <ul class="erros">
            <li v-for="e in previa.erros" :key="e">{{ e }}</li>
          </ul>
        </el-collapse-item>
      </el-collapse>
    </template>
    <template #footer>
      <el-button
        :disabled="nadaAFazer"
        :loading="aplicando"
        :type="remover ? 'danger' : 'primary'"
        @click="confirmar"
        >Aplicar</el-button
      >
      <el-button @click="visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import { ElLoading } from 'element-plus'
import type { ImportacaoItem, ImportacaoPrevia, ImportacaoResultado } from '@/api/monitoramento'

defineOptions({ name: 'ImportarPlanilha' })

const props = defineProps<{
  nomeSingular: string
  nomePlural: string
  analisar: (arquivo: File) => Promise<ImportacaoPrevia>
  aplicar: (arquivo: File, removerIds: number[]) => Promise<ImportacaoResultado>
}>()
const emit = defineEmits<{ importado: [] }>()
const message = useMessage()

const entrada = ref<HTMLInputElement>()
const visivel = ref(false)
const arquivo = ref<File>()
const previa = ref<ImportacaoPrevia>()
const remover = ref(false)
const aplicando = ref(false)
const abertos = ref<string[]>([])

const rotulo = (n: number) => (n === 1 ? props.nomeSingular : props.nomePlural)
const resumo = (i: ImportacaoItem) => i.mudancas.map((m) => `${m.campo}: ${m.para}`).join(' · ')

const dadosRemovidos = computed(
  () =>
    previa.value?.alterados.reduce((t, i) => t + i.mudancas.filter((m) => !m.para).length, 0) ?? 0
)
const nadaAFazer = computed(
  () => !previa.value?.novos.length && !previa.value?.alterados.length && !remover.value
)

const abrir = () => entrada.value?.click()

const escolhido = async () => {
  const f = entrada.value?.files?.[0]
  // permite escolher o mesmo arquivo de novo depois de corrigi-lo
  if (entrada.value) entrada.value.value = ''
  if (!f) return
  const carregando = ElLoading.service({ text: 'Analisando a planilha...' })
  try {
    const p = await props.analisar(f)
    arquivo.value = f
    previa.value = p
    remover.value = false
    // o que pode surpreender já vem aberto
    abertos.value = ['alterados', 'erros']
    visivel.value = true
  } finally {
    carregando.close()
  }
}

const confirmar = async () => {
  if (!arquivo.value || !previa.value) return
  const ids = remover.value ? previa.value.ausentes.map((i) => i.id!) : []
  if (ids.length) {
    await message.delConfirm(
      `Remover definitivamente ${ids.length} ${rotulo(ids.length)}? Esta ação não pode ser desfeita.`
    )
  }
  aplicando.value = true
  try {
    const r = await props.aplicar(arquivo.value, ids)
    message.success(
      `Importação concluída: ${r.adicionados} adicionado(s), ${r.atualizados} atualizado(s), ${r.removidos} removido(s).`
    )
    visivel.value = false
    emit('importado')
  } finally {
    aplicando.value = false
  }
}

defineExpose({ abrir })
</script>

<style scoped>
.nota {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.lista {
  max-height: 260px;
  padding-left: 18px;
  margin: 0;
  overflow: auto;
}

.lista li {
  margin-bottom: 6px;
}

.adicionado {
  color: var(--el-color-success);
}

.removido {
  color: var(--el-color-danger);
  text-decoration: line-through;
}

.erros {
  max-height: 220px;
  padding-left: 18px;
  margin: 0;
  overflow: auto;
  font-size: 13px;
  color: var(--el-color-danger);
}
</style>
