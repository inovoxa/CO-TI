<!-- Importar do Uptime Kuma: envia o kuma.db, mostra o que entra e importa -->
<template>
  <Dialog v-model="visivel" title="Importar do Uptime Kuma" width="900px">
    <template v-if="!analise && !resultado">
      <p class="mt-0 text-13px">
        Envie o arquivo <code>kuma.db</code> da pasta <code>data</code> do Uptime Kuma (pare o Kuma
        antes de copiar). Nada é gravado até você confirmar. O que o Kuma não tem — unidade, papel
        no telão e chamado automático — você preenche depois em cada monitor.
      </p>
      <el-upload
        :auto-upload="false"
        :limit="1"
        :on-change="escolher"
        :show-file-list="false"
        accept=".db,.sqlite"
        drag
      >
        <Icon :size="36" icon="ep:upload-filled" />
        <div class="el-upload__text">Arraste o kuma.db aqui ou <em>clique para escolher</em></div>
      </el-upload>
      <el-progress v-if="progresso !== null" :percentage="progresso" class="mt-12px" />
    </template>

    <template v-else-if="analise && !resultado">
      <el-tabs>
        <el-tab-pane :label="`Monitores (${importaveis.length} de ${analise.monitores.length})`">
          <el-table
            ref="tabela"
            :data="analise.monitores"
            max-height="380"
            size="small"
            @selection-change="(l: Mon.ItemKuma[]) => (escolhidos = l.map((x) => x.kumaId))"
          >
            <el-table-column
              :selectable="(r: Mon.ItemKuma) => !!r.tipo && !r.jaImportado"
              type="selection"
              width="40"
            />
            <el-table-column label="Monitor" min-width="200">
              <template #default="{ row }">
                <div class="font-medium">{{ row.nome }}</div>
                <div class="break-all text-12px text-[var(--el-text-color-secondary)]">{{
                  row.alvo
                }}</div>
              </template>
            </el-table-column>
            <el-table-column label="No Kuma" prop="tipoKuma" width="100" />
            <el-table-column label="No CO-TI" width="130">
              <template #default="{ row }">
                <span v-if="row.tipo">{{ rotuloTipo(row.tipo) }}</span>
                <el-tag v-else disable-transitions size="small" type="info">não entra</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="Ativo" width="70">
              <template #default="{ row }">{{ row.ativo ? 'sim' : 'pausado' }}</template>
            </el-table-column>
            <el-table-column label="Atenção" min-width="220">
              <template #default="{ row }">
                <span class="text-12px text-[var(--el-color-warning)]">{{ row.aviso }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`Canais de aviso (${analise.canais.filter((c) => c.tipo).length})`">
          <el-table :data="analise.canais" max-height="380" size="small">
            <el-table-column label="Canal" min-width="180" prop="nome" />
            <el-table-column label="No Kuma" prop="tipoKuma" width="120" />
            <el-table-column label="No CO-TI" width="120">
              <template #default="{ row }">{{ row.tipo || 'não entra' }}</template>
            </el-table-column>
            <el-table-column label="Atenção" min-width="240" prop="aviso" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`Manutenções (${analise.manutencoes.filter((c) => c.tipo).length})`">
          <el-table :data="analise.manutencoes" max-height="380" size="small">
            <el-table-column label="Manutenção" min-width="180" prop="nome" />
            <el-table-column label="No Kuma" prop="tipoKuma" width="150" />
            <el-table-column label="No CO-TI" width="110">
              <template #default="{ row }">{{ row.tipo || 'não entra' }}</template>
            </el-table-column>
            <el-table-column label="Atenção" min-width="220" prop="aviso" />
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <el-form class="mt-12px" label-width="190px">
        <el-form-item label="Histórico">
          <el-select v-model="opcoes.historicoDias" class="!w-220px">
            <el-option :value="0" label="Não trazer" />
            <el-option :value="1" label="Último dia" />
            <el-option :value="7" label="Últimos 7 dias" />
            <el-option :value="30" label="Últimos 30 dias" />
          </el-select>
          <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">
            {{ analise.batidas30d.toLocaleString('pt-BR') }} verificações nos últimos 30 dias
          </span>
        </el-form-item>
        <el-form-item label="">
          <el-checkbox v-model="opcoes.canais"
            >Importar os canais de aviso (tokens guardados cifrados)</el-checkbox
          >
        </el-form-item>
        <el-form-item label="">
          <el-checkbox v-model="opcoes.manutencoes">Importar as janelas de manutenção</el-checkbox>
        </el-form-item>
        <el-form-item label="Unidade de todos (opcional)">
          <el-select
            v-model="opcoes.unidadeId"
            class="!w-300px"
            clearable
            filterable
            placeholder="definir depois, um a um"
          >
            <el-option v-for="u in unidades" :key="u.id" :label="u.nome" :value="u.id!" />
          </el-select>
        </el-form-item>
      </el-form>
    </template>

    <template v-else-if="resultado">
      <el-result icon="success" title="Importação concluída">
        <template #sub-title>
          {{ resultado.monitores }} monitores, {{ resultado.canais }} canais,
          {{ resultado.manutencoes }} manutenções e
          {{ resultado.batidas.toLocaleString('pt-BR') }} verificações do histórico.
        </template>
      </el-result>
      <el-alert
        v-if="resultado.avisos.length"
        :closable="false"
        show-icon
        title="Para revisar"
        type="warning"
      >
        <ul class="m-0 pl-16px">
          <li v-for="(a, i) in resultado.avisos" :key="i">{{ a }}</li>
        </ul>
      </el-alert>
      <p class="text-13px">
        Agora, em cada monitor, escolha a unidade, o papel no telão (link principal) e se ele abre
        chamado ao cair.
      </p>
    </template>

    <template #footer>
      <template v-if="analise && !resultado">
        <el-button
          :disabled="!escolhidos.length"
          :loading="importando"
          type="primary"
          @click="confirmar"
        >
          Importar {{ escolhidos.length }} monitores
        </el-button>
        <el-button @click="reiniciar">Outro arquivo</el-button>
      </template>
      <el-button @click="visivel = false">{{ resultado ? 'Fechar' : 'Cancelar' }}</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import type { UploadFile } from 'element-plus'
import * as Mon from '@/api/monitoramento'
import { TIPOS } from '@/api/monitoramento'

const emit = defineEmits<{ importado: [] }>()
const visivel = ref(false)
const progresso = ref<number | null>(null)
const analise = ref<Mon.AnaliseKuma>()
const resultado = ref<Mon.ResultadoKuma>()
const importando = ref(false)
const escolhidos = ref<number[]>([])
const unidades = ref<Mon.Unidade[]>([])
const tabela = ref()
const opcoes = reactive({
  historicoDias: 7,
  canais: true,
  manutencoes: true,
  unidadeId: undefined as number | undefined
})

const importaveis = computed(() =>
  (analise.value?.monitores || []).filter((m) => m.tipo && !m.jaImportado)
)
const rotuloTipo = (t: string) => TIPOS.find((x) => x.valor === t)?.rotulo ?? t

const abrir = async () => {
  reiniciar()
  visivel.value = true
  unidades.value = await Mon.getUnidades()
}
defineExpose({ abrir })

const reiniciar = () => {
  analise.value = undefined
  resultado.value = undefined
  progresso.value = null
  escolhidos.value = []
}

const escolher = async (f: UploadFile) => {
  if (!f.raw) return
  progresso.value = 0
  try {
    analise.value = await Mon.analisarKuma(f.raw, (p) => (progresso.value = p))
    await nextTick()
    importaveis.value.forEach((m) => tabela.value?.toggleRowSelection(m, true))
  } finally {
    progresso.value = null
  }
}

const confirmar = async () => {
  if (!analise.value) return
  importando.value = true
  try {
    const ignorar = analise.value.monitores
      .filter((m) => !escolhidos.value.includes(m.kumaId))
      .map((m) => m.kumaId)
    resultado.value = await Mon.confirmarKuma({ token: analise.value.token, ignorar, ...opcoes })
    emit('importado')
  } finally {
    importando.value = false
  }
}
</script>
