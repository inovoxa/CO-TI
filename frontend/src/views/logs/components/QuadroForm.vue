<!-- Criar ou editar um quadro do painel. -->
<template>
  <Dialog v-model="aberto" :title="form.id && existente ? 'Editar quadro' : 'Novo quadro'" width="640px">
    <el-form ref="formRef" :model="form" :rules="regras" label-width="130px">
      <el-form-item label="Tipo" prop="tipo">
        <el-radio-group v-model="form.tipo">
          <el-radio-button value="contador">Contador</el-radio-button>
          <el-radio-button value="mensagens">Mensagens</el-radio-button>
          <el-radio-button value="ranking">Ranking</el-radio-button>
          <el-radio-button value="histograma">Histograma</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="Título" prop="titulo">
        <el-input v-model="form.titulo" maxlength="80" placeholder="Ex.: Contas bloqueadas" />
      </el-form-item>
      <el-form-item label="Fluxo">
        <el-select v-model="form.fluxo" class="!w-220px" placeholder="Todos os fluxos">
          <el-option v-for="f in Api.FLUXOS" :key="f.valor" :label="f.rotulo" :value="f.valor" />
        </el-select>
      </el-form-item>
      <el-form-item label="Consulta">
        <el-input
          v-model="form.consulta"
          placeholder="Ex.: event_id:4740   ou   action:blocked AND -catdesc:&quot;Games&quot;"
          class="font-mono"
        />
        <div class="dica">
          Texto livre ou <code>campo:valor</code>, com AND, OR, NOT, <code>-campo:valor</code>, aspas,
          <code>*</code> e <code>campo:&lt;=3</code>. Vazio = tudo do fluxo.
        </div>
      </el-form-item>
      <el-form-item label="Período">
        <el-select v-model="form.periodo" class="!w-220px">
          <el-option v-for="p in Api.PERIODOS" :key="p.valor" :label="p.rotulo" :value="p.valor" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.tipo === 'ranking'" label="Campo" prop="campo">
        <el-select
          v-model="form.campo"
          allow-create
          filterable
          default-first-option
          placeholder="Ex.: hostname, usuario, srcip"
          class="!w-260px"
        >
          <el-option v-for="c in campos" :key="c" :label="c" :value="c" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.tipo === 'ranking'" label="Itens">
        <el-input-number v-model="form.limite" :min="3" :max="100" />
      </el-form-item>
      <el-form-item v-if="form.tipo === 'contador'" label="Contar distintos">
        <el-select
          v-model="form.distinto"
          allow-create
          clearable
          filterable
          default-first-option
          placeholder="Vazio = contar mensagens"
          class="!w-260px"
        >
          <el-option v-for="c in campos" :key="c" :label="c" :value="c" />
        </el-select>
        <div class="dica">Ex.: <code>usuario</code> conta pessoas diferentes, não logons.</div>
      </el-form-item>
      <el-form-item v-if="form.tipo !== 'mensagens'" label="Medir">
        <el-select v-model="form.soma" class="!w-260px">
          <el-option v-for="m in Api.MEDIDAS" :key="m.valor" :label="m.rotulo" :value="m.valor" />
        </el-select>
        <div class="dica">
          Banda soma os bytes dos logs de sessão do FortiGate: use <code>type:traffic AND dstintfrole:wan</code> para
          a internet.
        </div>
      </el-form-item>
      <el-form-item v-if="form.tipo === 'mensagens'" label="Colunas">
        <el-select
          v-model="form.colunas"
          allow-create
          filterable
          multiple
          default-first-option
          placeholder="Campos ao lado do horário e da origem"
          class="w-full"
        >
          <el-option v-for="c in campos" :key="c" :label="c" :value="c" />
        </el-select>
      </el-form-item>
      <el-form-item label="Largura">
        <el-radio-group v-model="form.largura">
          <el-radio-button v-for="n in 4" :key="n" :value="n">{{ n }} coluna{{ n > 1 ? 's' : '' }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="aberto = false">Cancelar</el-button>
      <el-button type="primary" @click="salvar">Salvar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Api from '@/api/logs'

const emit = defineEmits<{ (e: 'salvar', q: Api.Quadro): void }>()

/** Campos comuns, para sugerir (o campo pode ser digitado livremente) */
const campos = [
  'usuario',
  'computador',
  'event_id',
  'acao',
  'ip_origem',
  'objeto',
  'compartilhamento',
  'hostname',
  'srcip',
  'dstip',
  'action',
  'catdesc',
  'subtype',
  'user',
  'service',
  'dstcountry',
  'origem',
  'aplicacao',
  'nivel',
  'fluxo'
]

const aberto = ref(false)
const existente = ref(false)
const formRef = ref()
const form = ref<Api.Quadro>(novo())

function novo(): Api.Quadro {
  return {
    id: 'q' + Date.now().toString(36),
    tipo: 'contador',
    titulo: '',
    fluxo: '',
    consulta: '',
    periodo: '1d',
    largura: 1,
    limite: 10,
    colunas: [],
    soma: ''
  }
}

const regras = {
  titulo: [{ required: true, message: 'Informe o título', trigger: 'blur' }],
  campo: [{ required: true, message: 'Escolha o campo do ranking', trigger: 'change' }]
}

const abrir = (q?: Api.Quadro) => {
  existente.value = !!q
  form.value = q ? JSON.parse(JSON.stringify({ limite: 10, colunas: [], soma: '', ...q })) : novo()
  aberto.value = true
}
defineExpose({ abrir })

watch(
  () => form.value.tipo,
  (t) => {
    if (!existente.value) form.value.largura = t === 'mensagens' || t === 'histograma' ? 4 : t === 'ranking' ? 2 : 1
  }
)

const salvar = async () => {
  await formRef.value.validate()
  const q = { ...form.value }
  if (q.tipo !== 'ranking') delete q.campo
  if (q.tipo !== 'contador' || !q.distinto) delete q.distinto
  if (q.tipo !== 'mensagens') delete q.colunas
  if (q.tipo !== 'ranking') delete q.limite
  if (q.tipo === 'mensagens' || !q.soma) delete q.soma
  emit('salvar', q)
  aberto.value = false
}
</script>

<style lang="scss" scoped>
.dica {
  width: 100%;
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);

  code {
    padding: 0 3px;
    background: var(--el-fill-color);
    border-radius: 3px;
  }
}
</style>
