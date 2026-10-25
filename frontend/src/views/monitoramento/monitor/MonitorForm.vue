<!-- Criar e alterar monitor, com teste antes de salvar -->
<template>
  <Dialog v-model="visivel" :title="dados.id ? 'Alterar monitor' : 'Novo monitor'" width="760px">
    <el-form ref="formRef" :model="dados" :rules="regras" label-width="170px">
      <el-form-item label="Tipo" prop="tipo">
        <el-select
          v-model="dados.tipo"
          class="w-full"
          :disabled="!!dados.id && dados.tipo === 'push'"
        >
          <el-option v-for="t in TIPOS" :key="t.valor" :label="t.rotulo" :value="t.valor">
            <span>{{ t.rotulo }}</span>
            <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">{{
              t.ajuda
            }}</span>
          </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="Nome" prop="nome">
        <el-input
          v-model="dados.nome"
          maxlength="150"
          placeholder="Ex.: Link principal · UBS Centro"
        />
      </el-form-item>

      <!-- alvo de cada tipo -->
      <el-form-item v-if="http" label="URL" prop="alvo">
        <el-input
          v-model="dados.alvo"
          maxlength="1000"
          placeholder="https://sistema.prefeitura.sp.gov.br/saude"
        />
      </el-form-item>
      <template v-if="dados.tipo === 'tcp'">
        <el-form-item label="Host" prop="alvo">
          <el-input v-model="dados.alvo" placeholder="nome ou IP" />
        </el-form-item>
        <el-form-item label="Porta" prop="porta">
          <el-input-number v-model="dados.porta" :max="65535" :min="1" controls-position="right" />
        </el-form-item>
      </template>
      <el-form-item v-if="dados.tipo === 'ping'" label="Host" prop="alvo">
        <el-input v-model="dados.alvo" placeholder="nome ou IP (ex.: o roteador do link)" />
      </el-form-item>
      <template v-if="dados.tipo === 'dns'">
        <el-form-item label="Nome a consultar" prop="alvo">
          <el-input v-model="dados.alvo" placeholder="portal.prefeitura.sp.gov.br" />
        </el-form-item>
        <el-form-item label="Registro">
          <el-select v-model="dados.dnsTipo" class="!w-140px">
            <el-option
              v-for="t in ['A', 'AAAA', 'CNAME', 'MX', 'NS', 'TXT', 'SOA', 'SRV', 'PTR', 'CAA']"
              :key="t"
              :label="t"
              :value="t"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="Servidor DNS">
          <el-input
            v-model="dados.dnsServidor"
            placeholder="vazio = o do servidor; ex.: 10.0.0.10 ou 1.1.1.1:53"
          />
        </el-form-item>
        <el-form-item label="Resposta deve conter">
          <el-input v-model="dados.dnsEsperado" placeholder="opcional, ex.: 200.144.10.5" />
        </el-form-item>
      </template>
      <template v-if="dados.tipo === 'palavra'">
        <el-form-item label="Palavra-chave" prop="palavra">
          <el-input
            v-model="dados.palavra"
            maxlength="500"
            placeholder="texto que aparece na página quando está tudo bem"
          />
        </el-form-item>
        <el-form-item label="">
          <el-checkbox v-model="dados.palavraInvertida"
            >Cai se a palavra aparecer (ex.: "erro", "manutenção")</el-checkbox
          >
        </el-form-item>
      </template>
      <template v-if="dados.tipo === 'json'">
        <el-form-item label="Caminho no JSON" prop="jsonCaminho">
          <el-input
            v-model="dados.jsonCaminho"
            placeholder="ex.: status ou dados.servicos[0].estado"
          />
        </el-form-item>
        <el-form-item label="Condição">
          <el-select v-model="dados.jsonOperador" class="!w-110px mr-8px">
            <el-option
              v-for="o in ['==', '!=', '<', '<=', '>', '>=', 'contem']"
              :key="o"
              :label="o"
              :value="o"
            />
          </el-select>
          <el-input
            v-model="dados.jsonEsperado"
            class="!w-260px"
            placeholder="valor esperado, ex.: ok"
          />
        </el-form-item>
      </template>
      <el-alert
        v-if="dados.tipo === 'push'"
        :closable="false"
        class="mb-16px"
        show-icon
        type="info"
        title="O serviço chama a URL de push (mostrada depois de salvar) a cada intervalo. Sem sinal no intervalo, o monitor cai. A URL tem o mesmo formato do Uptime Kuma."
      />
      <el-alert
        v-if="dados.tipo === 'manual'"
        :closable="false"
        class="mb-16px"
        show-icon
        type="info"
        title="O estado é escolhido no detalhe do monitor (no ar ou fora do ar)."
      />

      <el-divider content-position="left">Verificação</el-divider>
      <el-form-item v-if="dados.tipo !== 'manual'" label="Intervalo (s)">
        <el-input-number
          v-model="dados.intervaloS"
          :max="86400"
          :min="5"
          controls-position="right"
        />
        <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">
          {{
            dados.tipo === 'push' ? 'tempo máximo sem sinal' : 'de quanto em quanto tempo verificar'
          }}
        </span>
      </el-form-item>
      <el-form-item v-if="!semVerificacao" label="Tempo limite (s)">
        <el-input-number
          v-model="dados.tempoLimiteS"
          :max="300"
          :min="1"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item v-if="dados.tipo !== 'manual'" label="Tentativas antes de cair">
        <el-input-number v-model="dados.tentativas" :max="20" :min="0" controls-position="right" />
        <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]"
          >falhas seguidas que ficam como "pendente"</span
        >
      </el-form-item>
      <el-form-item
        v-if="dados.tipo !== 'manual' && dados.tentativas"
        label="Intervalo das tentativas (s)"
      >
        <el-input-number
          v-model="dados.intervaloTentativaS"
          :max="86400"
          :min="5"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item v-if="!semVerificacao" label="Lento acima de (ms)">
        <el-input-number
          v-model="dados.lentoMs"
          :min="0"
          controls-position="right"
          placeholder="opcional"
        />
      </el-form-item>
      <el-form-item v-if="!semVerificacao" label="">
        <el-checkbox v-model="dados.invertido"
          >Invertido: está "no ar" quando falha (ex.: porta que deve estar fechada)</el-checkbox
        >
      </el-form-item>

      <template v-if="http">
        <el-divider content-position="left">HTTP</el-divider>
        <el-form-item label="Método">
          <el-select v-model="dados.metodo" class="!w-140px">
            <el-option
              v-for="m in ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']"
              :key="m"
              :label="m"
              :value="m"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!['GET', 'HEAD'].includes(dados.metodo || 'GET')" label="Corpo">
          <el-input v-model="dados.corpo" :rows="3" type="textarea" />
        </el-form-item>
        <el-form-item label="Cabeçalhos (JSON)">
          <el-input
            v-model="dados.cabecalhos"
            :rows="2"
            placeholder="{&quot;Authorization&quot;: &quot;Bearer ...&quot;}"
            type="textarea"
          />
        </el-form-item>
        <el-form-item label="Códigos aceitos">
          <el-input v-model="dados.codigosAceitos" class="!w-240px" placeholder="200-299" />
        </el-form-item>
        <el-form-item label="Máx. redirecionamentos">
          <el-input-number
            v-model="dados.maxRedirecionamentos"
            :max="30"
            :min="0"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="Avisar certificado (dias)">
          <el-input-number
            v-model="dados.certAvisoDias"
            :max="365"
            :min="0"
            controls-position="right"
          />
          <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]">0 = não avisa</span>
        </el-form-item>
        <el-form-item label="">
          <el-checkbox v-model="dados.ignorarTls"
            >Ignorar erros de TLS (certificado vencido ou autoassinado)</el-checkbox
          >
        </el-form-item>
      </template>

      <el-divider content-position="left">Telão e unidade</el-divider>
      <el-form-item label="Unidade">
        <el-select
          v-model="dados.unidadeId"
          class="w-full"
          clearable
          filterable
          placeholder="nenhuma"
        >
          <el-option v-for="u in unidades" :key="u.id" :label="u.nome" :value="u.id!" />
        </el-select>
      </el-form-item>
      <el-form-item label="Papel">
        <el-radio-group v-model="dados.papel">
          <el-radio-button v-for="p in PAPEIS" :key="p.valor" :value="p.valor">{{
            p.rotulo
          }}</el-radio-button>
        </el-radio-group>
        <div
          v-if="dados.papel === 'principal'"
          class="w-full text-12px text-[var(--el-text-color-secondary)]"
        >
          O link principal define a cor da unidade no telão (fora do ar = vermelho).
        </div>
      </el-form-item>

      <el-divider content-position="left">Avisos e chamado</el-divider>
      <el-form-item label="Canais de aviso">
        <el-select v-model="dados.canalIds" class="w-full" multiple placeholder="nenhum">
          <el-option
            v-for="c in canais"
            :key="c.id"
            :label="`${c.nome} (${c.tipo})`"
            :value="c.id!"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="Repetir aviso a cada">
        <el-input-number v-model="dados.reenviarACada" :min="0" controls-position="right" />
        <span class="ml-8px text-12px text-[var(--el-text-color-secondary)]"
          >verificações fora (0 = não repete)</span
        >
      </el-form-item>
      <el-form-item label="">
        <el-checkbox v-model="dados.abrirChamado">Abrir chamado quando cair</el-checkbox>
      </el-form-item>
      <template v-if="dados.abrirChamado">
        <el-form-item label="Categoria">
          <el-select
            v-model="dados.chamadoCategoriaId"
            class="w-full"
            clearable
            filterable
            placeholder="sem categoria"
          >
            <el-option v-for="c in categorias" :key="c.id" :label="c.nomeCompleto" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="Urgência">
          <el-rate
            v-model="dados.chamadoUrgencia"
            :max="5"
            :texts="['Muito baixa', 'Baixa', 'Média', 'Alta', 'Muito alta']"
            show-text
          />
        </el-form-item>
        <el-form-item label="">
          <el-checkbox v-model="dados.chamadoSolucionar"
            >Solucionar o chamado sozinho quando voltar ao ar</el-checkbox
          >
        </el-form-item>
      </template>
      <el-form-item label="Descrição">
        <el-input v-model="dados.descricao" :rows="2" type="textarea" />
      </el-form-item>
    </el-form>

    <el-alert
      v-if="teste"
      :closable="false"
      :title="
        `${teste.ok ? 'Respondeu como esperado' : 'Falhou'}: ${teste.msg}` +
        (teste.latenciaMs != null ? ` (${teste.latenciaMs} ms)` : '') +
        (teste.certDias != null ? ` · certificado vence em ${teste.certDias} dias` : '')
      "
      :type="teste.ok ? 'success' : 'error'"
      show-icon
    />
    <template #footer>
      <el-button v-if="!semVerificacao" :loading="testando" @click="testar">Testar agora</el-button>
      <el-button :loading="salvando" type="primary" @click="salvar">Salvar</el-button>
      <el-button @click="visivel = false">Cancelar</el-button>
    </template>
  </Dialog>
</template>

<script lang="ts" setup>
import * as Mon from '@/api/monitoramento'
import { PAPEIS, TIPOS } from '@/api/monitoramento'
import * as Itsm from '@/api/itsm'

const emit = defineEmits<{ salvo: [id: number] }>()
const message = useMessage()
const visivel = ref(false)
const formRef = ref()
const salvando = ref(false)
const testando = ref(false)
const teste = ref<{ ok: boolean; latenciaMs?: number; msg: string; certDias?: number }>()
const canais = ref<Mon.Canal[]>([])
const unidades = ref<Mon.Unidade[]>([])
const categorias = ref<Itsm.Categoria[]>([])
const dados = ref<Mon.Monitor>(novo())

const http = computed(() => ['http', 'palavra', 'json'].includes(dados.value.tipo))
const semVerificacao = computed(() => ['push', 'manual'].includes(dados.value.tipo))
const regras = computed(() => ({
  nome: [{ required: true, message: 'Informe o nome', trigger: 'blur' }],
  tipo: [{ required: true, message: 'Escolha o tipo', trigger: 'change' }],
  alvo: semVerificacao.value
    ? []
    : [{ required: true, message: 'Informe o alvo', trigger: 'blur' }],
  porta:
    dados.value.tipo === 'tcp'
      ? [{ required: true, message: 'Informe a porta', trigger: 'blur' }]
      : [],
  palavra:
    dados.value.tipo === 'palavra'
      ? [{ required: true, message: 'Informe a palavra', trigger: 'blur' }]
      : []
}))

function novo(): Mon.Monitor {
  return {
    nome: '',
    tipo: 'http',
    metodo: 'GET',
    codigosAceitos: '200-299',
    jsonOperador: '==',
    dnsTipo: 'A',
    intervaloS: 60,
    tentativas: 1,
    intervaloTentativaS: 30,
    tempoLimiteS: 30,
    reenviarACada: 0,
    maxRedirecionamentos: 10,
    certAvisoDias: 14,
    papel: 'servico',
    chamadoUrgencia: 4,
    chamadoSolucionar: true,
    ativo: true
  }
}

const abrir = async (detalhe?: Mon.Detalhe) => {
  teste.value = undefined
  const [c, u, cat] = await Promise.all([
    Mon.getCanais(),
    Mon.getUnidades(),
    Itsm.getCategorias(true).catch(() => [])
  ])
  canais.value = c
  unidades.value = u
  categorias.value = cat
  dados.value = detalhe
    ? { ...detalhe.config }
    : { ...novo(), canalIds: c.filter((x) => x.padrao).map((x) => x.id!) }
  visivel.value = true
}
defineExpose({ abrir })

const testar = async () => {
  await formRef.value.validate()
  testando.value = true
  try {
    teste.value = await Mon.testarMonitor(dados.value)
  } finally {
    testando.value = false
  }
}
const salvar = async () => {
  await formRef.value.validate()
  salvando.value = true
  try {
    const id = await Mon.salvarMonitor(dados.value)
    message.success('Monitor salvo')
    visivel.value = false
    emit('salvo', id)
  } finally {
    salvando.value = false
  }
}
</script>
