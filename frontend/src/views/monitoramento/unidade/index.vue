<!-- Rota: /monitoramento/unidades · Permissões: monitoramento:monitor:query (ver) e monitoramento:unidade:update -->
<template>
  <ContentWrap>
    <div class="mb-12px flex items-center justify-between">
      <span class="text-13px text-[var(--el-text-color-secondary)]">
        Unidades (filiais) que aparecem no mapa do telão. O CEP preenche o endereço; com o número, o
        ponto é calculado e pode ser ajustado no mapa.
      </span>
      <el-button v-if="pode" type="primary" @click="abrir()"
        ><Icon class="mr-4px" icon="ep:plus" />Nova unidade</el-button
      >
    </div>
    <el-table v-loading="loading" :data="lista">
      <el-table-column label="Unidade" min-width="200">
        <template #default="{ row }">
          <span class="font-medium">{{ row.nome }}</span>
          <el-tag v-if="!row.ativa" class="ml-6px" disable-transitions size="small" type="info"
            >inativa</el-tag
          >
          <div class="text-12px text-[var(--el-text-color-secondary)]">{{ row.deptNome }}</div>
        </template>
      </el-table-column>
      <el-table-column label="Endereço" min-width="280">
        <template #default="{ row }">{{ endereco(row) || '—' }}</template>
      </el-table-column>
      <el-table-column label="Coordenadas" width="190">
        <template #default="{ row }">
          <span class="text-12px"
            >{{ Number(row.latitude).toFixed(5) }}, {{ Number(row.longitude).toFixed(5) }}</span
          >
        </template>
      </el-table-column>
      <el-table-column align="right" label="Monitores" prop="monitores" width="100" />
      <el-table-column v-if="pode" label="" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="abrir(row)">Editar</el-button>
          <el-button link type="danger" @click="excluir(row)">Excluir</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty
      v-if="!loading && !lista.length"
      :image-size="60"
      description="Nenhuma unidade cadastrada."
    />
  </ContentWrap>

  <Dialog
    v-model="form.visivel"
    :title="form.dados.id ? 'Alterar unidade' : 'Nova unidade'"
    width="760px"
  >
    <el-form ref="formRef" :model="form.dados" :rules="regras" label-width="120px">
      <el-form-item label="Nome" prop="nome"
        ><el-input v-model="form.dados.nome" maxlength="100"
      /></el-form-item>
      <el-form-item label="Departamento" prop="deptId">
        <el-tree-select
          v-model="form.dados.deptId"
          :data="depts"
          :props="defaultProps"
          check-strictly
          class="w-full"
          node-key="id"
          placeholder="departamento raiz da unidade"
        />
      </el-form-item>
      <el-form-item label="CEP">
        <el-input
          v-model="form.dados.cep"
          class="!w-150px"
          maxlength="9"
          placeholder="00000-000"
          @keyup.enter="buscarCep"
        />
        <el-button :loading="buscando" class="ml-8px" @click="buscarCep">Buscar endereço</el-button>
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="16">
          <el-form-item label="Logradouro"
            ><el-input v-model="form.dados.logradouro" maxlength="200"
          /></el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="Número" label-width="70px"
            ><el-input v-model="form.dados.numero" maxlength="20"
          /></el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="Complemento"
            ><el-input v-model="form.dados.complemento" maxlength="100"
          /></el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Bairro" label-width="70px"
            ><el-input v-model="form.dados.bairro" maxlength="100"
          /></el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="12">
        <el-col :span="16">
          <el-form-item label="Cidade"
            ><el-input v-model="form.dados.cidade" maxlength="100"
          /></el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="UF" label-width="70px"
            ><el-input v-model="form.dados.uf" maxlength="2"
          /></el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="Localização" prop="latitude">
        <div class="w-full">
          <div class="mb-8px flex flex-wrap items-center gap-8px">
            <el-button :loading="localizando" @click="localizar">Localizar no mapa</el-button>
            <el-input-number
              v-model="form.dados.latitude"
              :controls="false"
              :precision="7"
              class="!w-140px"
              placeholder="latitude"
            />
            <el-input-number
              v-model="form.dados.longitude"
              :controls="false"
              :precision="7"
              class="!w-140px"
              placeholder="longitude"
            />
          </div>
          <el-alert
            v-if="aviso"
            :closable="false"
            :title="aviso"
            class="mb-8px"
            show-icon
            type="warning"
          />
          <MapaPonto
            v-if="form.visivel"
            ref="mapa"
            :latitude="form.dados.latitude"
            :longitude="form.dados.longitude"
            @mover="
              (lat, lng) => {
                form.dados.latitude = lat
                form.dados.longitude = lng
                aviso = ''
              }
            "
          />
          <div class="mt-4px text-12px text-[var(--el-text-color-secondary)]"
            >Clique no mapa ou arraste o ponto para ajustar.</div
          >
        </div>
      </el-form-item>
      <el-form-item label="">
        <el-checkbox v-model="form.dados.ativa">Ativa (aparece no telão)</el-checkbox>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="form.visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Mon from '@/api/monitoramento'
import * as DeptApi from '@/api/system/dept'
import { checkPermi } from '@/utils/permission'
import { defaultProps, handleTree } from '@/utils/tree'
import MapaPonto from './MapaPonto.vue'

defineOptions({ name: 'MonitoramentoUnidade' })
const message = useMessage()
const pode = checkPermi(['monitoramento:unidade:update'])
const regras = {
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  deptId: [{ required: true, message: 'Escolha o departamento', trigger: 'change' }],
  latitude: [{ required: true, message: 'Localize a unidade no mapa', trigger: 'change' }]
}
const PRECISAO: Record<string, string> = {
  rua: 'Achei a rua, mas não o número: confira e ajuste o ponto no mapa.',
  cep: 'Achei só o CEP: confira e ajuste o ponto no mapa.',
  cidade: 'Achei só a cidade: arraste o ponto até o endereço da unidade.'
}
const lista = ref<Mon.Unidade[]>([])
const loading = ref(true)
const depts = ref<any[]>([])
const formRef = ref()
const mapa = ref<InstanceType<typeof MapaPonto>>()
const form = reactive({ visivel: false, dados: {} as Mon.Unidade })
const buscando = ref(false)
const localizando = ref(false)
const aviso = ref('')

const endereco = (u: Mon.Unidade) =>
  [
    [u.logradouro, u.numero].filter(Boolean).join(', '),
    u.bairro,
    [u.cidade, u.uf].filter(Boolean).join('/'),
    u.cep
  ]
    .filter(Boolean)
    .join(' · ')

const carregar = async () => {
  loading.value = true
  try {
    lista.value = await Mon.getUnidades()
  } finally {
    loading.value = false
  }
}
const abrir = async (u?: Mon.Unidade) => {
  if (!depts.value.length) depts.value = handleTree(await DeptApi.getSimpleDeptList())
  form.dados = u
    ? { ...u, latitude: Number(u.latitude), longitude: Number(u.longitude) }
    : { nome: '', ativa: true }
  aviso.value = ''
  form.visivel = true
}
const buscarCep = async () => {
  if (!form.dados.cep) return
  buscando.value = true
  try {
    const e = await Mon.buscarCep(form.dados.cep)
    form.dados.cep = e.cep
    form.dados.logradouro = e.logradouro || form.dados.logradouro
    form.dados.bairro = e.bairro || form.dados.bairro
    form.dados.cidade = e.cidade || form.dados.cidade
    form.dados.uf = e.uf || form.dados.uf
    if (!form.dados.numero)
      message.info('Endereço preenchido. Informe o número e clique em "Localizar no mapa".')
  } finally {
    buscando.value = false
  }
}
const localizar = async () => {
  localizando.value = true
  try {
    const c = await Mon.geocodificar(form.dados)
    form.dados.latitude = Number(c.latitude)
    form.dados.longitude = Number(c.longitude)
    aviso.value = PRECISAO[c.precisao] || ''
    await nextTick()
    mapa.value?.centralizar()
  } finally {
    localizando.value = false
  }
}
const salvar = async () => {
  await formRef.value.validate()
  await Mon.salvarUnidade(form.dados)
  message.success('Unidade salva')
  form.visivel = false
  await carregar()
}
const excluir = async (u: Mon.Unidade) => {
  await message.delConfirm(`Excluir a unidade "${u.nome}"? Os monitores dela ficam sem unidade.`)
  await Mon.excluirUnidade(u.id!)
  await carregar()
}

onMounted(carregar)
</script>
