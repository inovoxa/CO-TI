<!-- Unidade pela faixa de IP: a máquina fica na unidade da faixa onde o IP dela cai (a mais específica). -->
<template>
  <ContentWrap title="Unidade por faixa de IP">
    <p class="ajuda mt-0">
      A unidade de cada máquina vem, nesta ordem: da unidade escolhida à mão no ativo; da
      <b>faixa de IP</b> abaixo (a mais específica); e, fora de qualquer faixa, da unidade da pessoa
      logada. Assim, máquina compartilhada ou sem usuário do AD fica na unidade onde está.
    </p>
    <div class="mb-10px flex flex-wrap gap-8px">
      <el-button v-if="pode" type="primary" @click="abrir()">
        <Icon class="mr-4px" icon="ep:plus" /> Nova faixa
      </el-button>
      <el-button v-if="pode" :loading="sugerindo" @click="sugerir">
        <Icon class="mr-4px" icon="ep:magic-stick" /> Sugerir pelas máquinas
      </el-button>
      <el-button v-if="pode && faixas.length" :loading="aplicando" @click="aplicar">
        <Icon class="mr-4px" icon="ep:refresh-right" /> Aplicar nos ativos agora
      </el-button>
      <el-button plain @click="exportar"
        ><Icon class="mr-4px" icon="ep:download" /> Exportar</el-button
      >
      <el-button v-if="pode" plain @click="importador?.abrir()">
        <Icon class="mr-4px" icon="ep:upload" /> Importar
      </el-button>
    </div>

    <el-table v-loading="carregando" :data="faixas" size="small">
      <el-table-column label="Faixa" prop="cidr" width="170" />
      <el-table-column label="Unidade" min-width="200" prop="deptNome" show-overflow-tooltip />
      <el-table-column align="right" label="Máquinas" prop="maquinas" width="100" />
      <el-table-column label="Observação" min-width="180" prop="observacao" show-overflow-tooltip />
      <el-table-column v-if="pode" align="right" label="" width="130">
        <template #default="{ row }">
          <el-button link type="primary" @click="abrir(row)">Editar</el-button>
          <el-button link type="danger" @click="excluir(row)">Excluir</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!carregando && !faixas.length"
      :image-size="60"
      description='Nenhuma faixa cadastrada: a unidade vem da pessoa logada. Use "Sugerir pelas máquinas" para começar.'
    />
  </ContentWrap>

  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Editar faixa' : 'Nova faixa'"
    width="480px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-position="top">
      <el-form-item label="Faixa de IP" prop="cidr">
        <el-input v-model="form.dados.cidr" placeholder="172.19.21.0/24" />
        <div class="ajuda">Sem a barra, "172.19.21.0" vale como /24.</div>
      </el-form-item>
      <el-form-item label="Unidade" prop="deptId">
        <el-tree-select
          v-model="form.dados.deptId"
          :data="depts"
          :props="defaultProps"
          check-strictly
          class="!w-full"
          filterable
          node-key="id"
          placeholder="Escolha a unidade"
        />
      </el-form-item>
      <el-form-item label="Observação">
        <el-input v-model="form.dados.observacao" maxlength="255" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :loading="salvando" type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>

  <Dialog v-model="sug.visivel" title="Faixas sugeridas" width="720px">
    <p class="ajuda mt-0">
      Redes /24 ainda sem faixa em que a maioria das máquinas (60% ou mais) já está numa mesma
      unidade, pela pessoa logada. Marque as que estão certas.
    </p>
    <el-table
      :data="sug.lista"
      max-height="420"
      size="small"
      @selection-change="(l: Inv.SugestaoFaixa[]) => (sug.escolhidas = l)"
    >
      <el-table-column type="selection" width="40" />
      <el-table-column label="Faixa" prop="cidr" width="160" />
      <el-table-column label="Unidade" min-width="200" prop="deptNome" show-overflow-tooltip />
      <el-table-column align="right" label="Máquinas" width="150">
        <template #default="{ row }">{{ row.naUnidade }} de {{ row.maquinas }} na unidade</template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!sug.lista.length"
      :image-size="60"
      description="Nenhuma sugestão: as redes já têm faixa ou as máquinas estão em unidades misturadas."
    />
    <template #footer>
      <el-button
        :disabled="!sug.escolhidas.length"
        :loading="salvando"
        type="primary"
        @click="cadastrarSugestoes"
        >Cadastrar {{ sug.escolhidas.length || '' }} e aplicar</el-button
      >
      <el-button @click="sug.visivel = false">Fechar</el-button>
    </template>
  </Dialog>

  <ImportarPlanilha
    ref="importador"
    :analisar="Inv.analisarImportacaoFaixas"
    :aplicar="Inv.importarFaixas"
    nome-plural="faixas"
    nome-singular="faixa"
    @importado="carregar"
  />
</template>

<script lang="ts" setup>
import * as Inv from '@/api/inventario'
import * as DeptApi from '@/api/system/dept'
import ImportarPlanilha from '@/components/ImportarPlanilha/index.vue'
import download from '@/utils/download'
import { checkPermi } from '@/utils/permission'
import { defaultProps, handleTree } from '@/utils/tree'

defineOptions({ name: 'InventarioFaixasUnidade' })
const emit = defineEmits<{ mudou: [] }>()
const message = useMessage()
const pode = checkPermi(['inventario:ativo:update'])
const regras = {
  cidr: [{ required: true, message: 'Informe a faixa', trigger: 'blur' }],
  deptId: [{ required: true, message: 'Escolha a unidade', trigger: 'change' }]
}

const faixas = ref<Inv.FaixaUnidade[]>([])
const carregando = ref(true)
const salvando = ref(false)
const aplicando = ref(false)
const sugerindo = ref(false)
const depts = ref<any[]>([])
const formRef = ref()
const importador = ref<InstanceType<typeof ImportarPlanilha>>()
const form = reactive({
  visivel: false,
  dados: {} as { id?: number; cidr: string; deptId?: number; observacao?: string }
})
const sug = reactive({
  visivel: false,
  lista: [] as Inv.SugestaoFaixa[],
  escolhidas: [] as Inv.SugestaoFaixa[]
})

const carregar = async () => {
  carregando.value = true
  try {
    faixas.value = await Inv.getFaixas()
  } finally {
    carregando.value = false
  }
}
const garantirDepts = async () => {
  if (!depts.value.length) depts.value = handleTree(await DeptApi.getSimpleDeptList())
}

const abrir = async (f?: Inv.FaixaUnidade) => {
  await garantirDepts()
  form.dados = f
    ? { id: f.id, cidr: f.cidr, deptId: f.deptId, observacao: f.observacao }
    : { cidr: '', deptId: undefined, observacao: '' }
  form.visivel = true
  nextTick(() => formRef.value?.clearValidate())
}
const aplicarDepois = async (texto: string) => {
  const n = await Inv.aplicarFaixas()
  message.success(n ? `${texto}; ${n} ativo(s) mudaram de unidade` : texto)
  await carregar()
  emit('mudou')
}
const salvar = async () => {
  if (!(await formRef.value?.validate().catch(() => false))) return
  salvando.value = true
  try {
    await Inv.salvarFaixa({ ...form.dados, deptId: form.dados.deptId! })
    form.visivel = false
    await aplicarDepois('Faixa salva')
  } finally {
    salvando.value = false
  }
}
const excluir = async (f: Inv.FaixaUnidade) => {
  try {
    await message.delConfirm(
      `Excluir a faixa ${f.cidr}? As máquinas dela voltam a seguir a unidade da pessoa logada no próximo inventário.`
    )
  } catch {
    return
  }
  await Inv.excluirFaixa(f.id)
  message.success('Faixa excluída')
  await carregar()
}
const aplicar = async () => {
  aplicando.value = true
  try {
    await aplicarDepois('Faixas aplicadas')
  } finally {
    aplicando.value = false
  }
}
const sugerir = async () => {
  sugerindo.value = true
  try {
    sug.lista = await Inv.getSugestoesFaixa()
    sug.escolhidas = []
    sug.visivel = true
  } finally {
    sugerindo.value = false
  }
}
const cadastrarSugestoes = async () => {
  salvando.value = true
  try {
    for (const s of sug.escolhidas) await Inv.salvarFaixa({ cidr: s.cidr, deptId: s.deptId })
    sug.visivel = false
    await aplicarDepois(`${sug.escolhidas.length} faixa(s) cadastrada(s)`)
  } finally {
    salvando.value = false
  }
}
const exportar = async () => download.excel(await Inv.exportarFaixas(), 'faixas-ip.xlsx')

onMounted(carregar)
</script>

<style lang="scss" scoped>
.ajuda {
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>
