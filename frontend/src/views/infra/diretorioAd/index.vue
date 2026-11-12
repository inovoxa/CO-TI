<template>
  <div v-loading="carregando">
    <ContentWrap v-if="tela">
      <div class="situacao">
        <el-tag :type="tela.dados.habilitado ? 'success' : 'info'" size="large">
          {{ tela.dados.habilitado ? 'Login pelo AD ligado' : 'Login pelo AD desligado' }}
        </el-tag>
        <el-tag v-if="tela.dados.habilitado && tela.conectado" type="success">Conectado</el-tag>
        <el-tag v-else-if="tela.dados.habilitado && tela.erroConexao" type="danger">Sem conexão</el-tag>
        <span class="ajuda">
          <template v-if="tela.origem === 'ambiente'">
            Valendo as variáveis de ambiente (CO_TI_AD_*). Ao salvar, passa a valer esta tela.
          </template>
          <template v-else>Alterado {{ quando(tela.atualizadoEm) }}</template>
        </span>
      </div>
      <el-alert v-if="tela.dados.habilitado && tela.erroConexao" :closable="false" :title="tela.erroConexao" class="mt-12px" show-icon type="error" />
      <p class="ajuda mt-12px">
        Quem está no AD entra no CO-TI com o mesmo usuário e senha da rede (ex.: <code>joao</code>, <code>PMA\joao</code> ou
        <code>joao@pma.local</code>). A conta é criada no primeiro acesso. O <b>admin</b> local continua entrando mesmo com o AD
        fora do ar.
      </p>
    </ContentWrap>

    <el-form v-if="form" ref="formRef" :disabled="!podeAlterar" :model="form" label-position="top">
      <ContentWrap title="Servidor">
        <el-form-item>
          <el-switch v-model="form.habilitado" active-text="Permitir que usuários do AD entrem no CO-TI" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :md="12" :xs="24">
            <el-form-item label="Servidor">
              <el-input v-model="form.servidores" placeholder="10.0.0.5 ou dc01.pma.local" />
              <div class="ajuda">Nome ou IP do controlador de domínio. Para ter reserva, separe vários por vírgula.</div>
            </el-form-item>
          </el-col>
          <el-col :md="6" :xs="12">
            <el-form-item label="Segurança">
              <el-select v-model="form.seguranca" @change="trocarSeguranca">
                <el-option label="LDAPS" value="ldaps" />
                <el-option label="StartTLS" value="starttls" />
                <el-option label="Sem criptografia" value="nenhuma" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :md="6" :xs="12">
            <el-form-item label="Porta">
              <el-input-number v-model="form.porta" :max="65535" :min="1" controls-position="right" />
            </el-form-item>
          </el-col>
        </el-row>
        <p class="ajuda">
          A senha de cada pessoa passa por esta conexão. LDAPS (636) é o mais comum; StartTLS cifra na 389. As duas exigem
          certificado instalado no DC.
        </p>
        <el-alert
          v-if="form.seguranca === 'nenhuma'"
          :closable="false"
          class="mt-12px"
          show-icon
          title="Sem criptografia, a senha de quem entra passa aberta pela rede até o DC"
          type="error"
        >
          Use só se o DC não tem certificado e o caminho entre o CO-TI e o DC é todo na rede interna, como no GLPI sem SSL. Quando o
          DC ganhar certificado, troque para LDAPS ou StartTLS.
        </el-alert>
        <el-form-item v-if="form.seguranca !== 'nenhuma'" class="mt-12px" label="Certificado da CA do AD">
          <el-input
            v-model="form.certificadoCa"
            :rows="4"
            placeholder="-----BEGIN CERTIFICATE-----  (vazio = usa as CAs conhecidas do servidor)"
            type="textarea"
          />
          <div class="linha-botoes">
            <el-button :loading="buscandoCert" @click="buscarCertificado">Buscar certificado do servidor</el-button>
            <span class="ajuda">Mostra o certificado que o DC apresenta, para você conferir e confiar.</span>
          </div>
        </el-form-item>
      </ContentWrap>

      <ContentWrap title="Conta de serviço">
        <el-row :gutter="16">
          <el-col :md="12" :xs="24">
            <el-form-item label="RootDN (conta de serviço)">
              <el-input v-model="form.bindDn" placeholder="svc.coti@pma.local ou CN=svc.coti,OU=Servicos,DC=pma,DC=local" />
              <div class="ajuda">Uma conta que só leia o AD já basta. Pode ser a mesma do GLPI.</div>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="Senha">
              <el-input
                v-model="senha"
                :placeholder="tela?.senhaSalva ? 'Senha salva. Deixe em branco para manter' : 'Senha da conta de serviço'"
                autocomplete="new-password"
                show-password
                type="password"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </ContentWrap>

      <ContentWrap title="Quem pode entrar">
        <el-row :gutter="16">
          <el-col :md="12" :xs="24">
            <el-form-item label="BaseDN">
              <el-input v-model="form.baseUsuarios" placeholder="DC=pma,DC=local" />
              <div class="ajuda">Onde procurar as contas. Tudo abaixo dele entra na busca.</div>
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="Campo de login">
              <el-input disabled model-value="sAMAccountName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="Filtro da conexão">
          <el-input
            v-model="form.filtro"
            :rows="2"
            placeholder="(memberOf=CN=GG-CO-TI,OU=Grupos,DC=pma,DC=local)"
            type="textarea"
          />
          <div class="ajuda">
            Opcional. Pode colar o mesmo filtro do GLPI. Ele é somado a "é uma pessoa", então computadores e grupos nunca entram.
            Sem filtro, qualquer conta dentro do BaseDN pode entrar.
          </div>
        </el-form-item>
      </ContentWrap>

      <ContentWrap title="Departamentos e perfis">
        <el-row :gutter="16">
          <el-col :md="12" :xs="24">
            <el-form-item label="Departamento de quem entra">
              <el-tree-select
                v-model="form.deptRaizId"
                :data="departamentos"
                :props="defaultProps"
                check-strictly
                clearable
                filterable
                node-key="id"
                placeholder="Departamento raiz"
              />
            </el-form-item>
          </el-col>
          <el-col :md="12" :xs="24">
            <el-form-item label="Perfil de quem entra pela primeira vez">
              <el-select v-model="form.perfilPadrao" clearable filterable placeholder="Nenhum">
                <el-option v-for="r in perfis" :key="r.code" :label="r.name" :value="r.code" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item>
          <el-switch v-model="form.espelharOus" active-text="Criar departamentos espelhando as OUs do AD" />
        </el-form-item>
        <p class="ajuda">
          <template v-if="form.espelharOus">
            Cada OU vira um departamento abaixo do escolhido acima, e a pessoa fica no da sua OU, atualizado a cada sincronização.
            O departamento que alguém mudar à mão no portal volta para o da OU.
          </template>
          <template v-else>
            Quem entra pela primeira vez vai para o departamento escolhido acima. Depois disso, o departamento é o que você definir
            no portal, e o AD não muda mais.
          </template>
        </p>
        <el-form-item v-if="form.espelharOus" class="mt-12px" label="OU raiz">
          <el-input v-model="form.ouRaiz" placeholder="vazio = o BaseDN" />
        </el-form-item>

        <el-form-item class="mt-12px" label="Grupos do AD que dão perfil no CO-TI">
          <div class="grupos">
            <div v-for="(g, i) in grupos" :key="i" class="grupo">
              <el-input v-model="g.grupo" placeholder="GG-TI-NOC ou o DN completo do grupo" />
              <el-select v-model="g.perfil" filterable placeholder="Perfil">
                <el-option v-for="r in perfis" :key="r.code" :label="r.name" :value="r.code" />
              </el-select>
              <el-button link type="danger" @click="grupos.splice(i, 1)">Remover</el-button>
            </div>
            <el-button link type="primary" @click="grupos.push({ grupo: '', perfil: '' })">+ Adicionar grupo</el-button>
            <div class="ajuda">
              Quem entra no grupo recebe o perfil e quem sai do grupo perde. Os perfis dados à mão no portal não mudam.
            </div>
          </div>
        </el-form-item>
        <el-form-item label="Grupo(s) de aprovadores">
          <el-input v-model="form.grupoAprovadores" placeholder="GG-Diretores; GG-Secretarios" />
          <div class="ajuda">Os membros aprovam pedidos. Para mais de um grupo, separe com ";".</div>
        </el-form-item>
      </ContentWrap>

      <ContentWrap title="Sincronização">
        <el-form-item>
          <el-switch v-model="form.desativarAusentes" active-text="Desativar no CO-TI quem for desativado ou excluído do AD" />
        </el-form-item>
        <p class="ajuda">
          Roda a cada 30 minutos e reativa quem voltar. Se mais de 20% das contas sumirem de uma vez, ninguém é desativado, porque
          o mais provável é um erro no BaseDN ou no filtro.
        </p>
      </ContentWrap>
    </el-form>

    <div v-if="form && podeAlterar" class="acoes">
      <el-button @click="abrirTeste">Testar conexão</el-button>
      <el-button :disabled="!tela?.dados.habilitado" :loading="sincronizando" @click="sincronizar">
        Sincronizar agora
      </el-button>
      <el-button :loading="salvando" type="primary" @click="salvar">Salvar</el-button>
    </div>

    <el-dialog v-model="testeAberto" title="Testar conexão" width="560px">
      <p class="ajuda mb-12px">
        Testa o que está na tela, sem salvar. Para conferir também o login de uma pessoa, informe o usuário e, se quiser, a senha
        dela.
      </p>
      <el-form label-position="top">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="Usuário (opcional)">
              <el-input v-model="teste.login" placeholder="joao" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Senha (opcional)">
              <el-input v-model="teste.senha" autocomplete="new-password" show-password type="password" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div v-if="resultado" class="resultado">
        <div v-for="(e, i) in resultado.etapas" :key="i" class="etapa ok">✓ {{ e }}</div>
        <div v-if="resultado.erro" class="etapa erro">✗ {{ resultado.erro }}</div>
        <div v-else class="etapa ok"><b>Tudo certo.</b> Salve para valer.</div>
      </div>
      <template #footer>
        <el-button @click="testeAberto = false">Fechar</el-button>
        <el-button :loading="testando" type="primary" @click="testar">Testar</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="certAberto" title="Certificados apresentados pelo servidor" width="680px">
      <p class="ajuda mb-12px">
        Confira com quem administra o AD se a impressão digital é a da CA da prefeitura antes de confiar. Na máquina do DC:
        <code>certutil -store CA</code> ou as propriedades do certificado, em Detalhes.
      </p>
      <el-checkbox-group v-model="certEscolhidos">
        <div v-for="(c, i) in certificados" :key="c.sha256" class="cert">
          <el-checkbox :value="i">
            <b>{{ c.ca ? 'Autoridade certificadora' : 'Certificado do servidor' }}</b>
          </el-checkbox>
          <div class="ajuda">Para: {{ c.assunto }}</div>
          <div class="ajuda">Emitido por: {{ c.emissor }}</div>
          <div class="ajuda">Válido até: {{ quando(c.validoAte) }}</div>
          <div class="ajuda sha">SHA-256: {{ c.sha256 }}</div>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="certAberto = false">Cancelar</el-button>
        <el-button :disabled="!certEscolhidos.length" type="primary" @click="confiar">Confiar nos marcados</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script lang="ts" setup>
import * as Infra from '@/api/infra/infraestrutura'
import * as DeptApi from '@/api/system/dept'
import * as RoleApi from '@/api/system/role'
import { checkPermi } from '@/utils/permission'
import { defaultProps, handleTree } from '@/utils/tree'

defineOptions({ name: 'InfraDiretorioAd' })

const message = useMessage()
const podeAlterar = checkPermi(['infra:ad:update'])
const carregando = ref(false)
const salvando = ref(false)
const sincronizando = ref(false)
const tela = ref<Infra.TelaAd>()
const form = ref<Infra.DadosAd>()
const senha = ref('')
const grupos = ref<{ grupo: string; perfil: string }[]>([])
const departamentos = ref<any[]>([])
const perfis = ref<RoleApi.RoleVO[]>([])

const testeAberto = ref(false)
const testando = ref(false)
const teste = reactive({ login: '', senha: '' })
const resultado = ref<Infra.TesteAd>()

const certAberto = ref(false)
const buscandoCert = ref(false)
const certificados = ref<Infra.CertificadoAd[]>([])
const certEscolhidos = ref<number[]>([])

const quando = (d: string | null) => (d ? new Date(d).toLocaleString('pt-BR') : '')

const carregar = async () => {
  carregando.value = true
  try {
    const [t, depts, roles] = await Promise.all([Infra.getAd(), DeptApi.getSimpleDeptList(), RoleApi.getSimpleRoleList()])
    tela.value = t
    form.value = { ...t.dados }
    grupos.value = Object.entries(t.dados.gruposPerfis || {}).map(([grupo, perfil]) => ({ grupo, perfil }))
    departamentos.value = handleTree(depts)
    perfis.value = roles
    senha.value = ''
  } finally {
    carregando.value = false
  }
}

const dados = (): Infra.DadosAd => ({
  ...form.value!,
  gruposPerfis: Object.fromEntries(grupos.value.filter((g) => g.grupo.trim() && g.perfil).map((g) => [g.grupo.trim(), g.perfil]))
})

const trocarSeguranca = (s: string) => {
  if (!form.value) return
  if (s !== 'ldaps' && form.value.porta === 636) form.value.porta = 389
  if (s === 'ldaps' && form.value.porta === 389) form.value.porta = 636
}

const salvar = async () => {
  const d = dados()
  if (d.seguranca === 'nenhuma' && tela.value?.dados.seguranca !== 'nenhuma') {
    await message.confirm('Salvar sem criptografia? As senhas de quem entra pelo AD vão passar abertas pela rede até o DC.')
  }
  if (d.habilitado && !tela.value?.dados.habilitado) {
    await message.confirm('Ligar o login pelo AD agora? Teste a conexão antes, se ainda não testou.')
  }
  salvando.value = true
  try {
    await Infra.salvarAd(d, senha.value)
    message.success('Salvo e aplicado')
    await carregar()
  } finally {
    salvando.value = false
  }
}

const abrirTeste = () => {
  resultado.value = undefined
  testeAberto.value = true
}

const testar = async () => {
  testando.value = true
  resultado.value = undefined
  try {
    resultado.value = await Infra.testarAd(dados(), senha.value, teste.login, teste.senha)
  } finally {
    testando.value = false
    teste.senha = ''
  }
}

const buscarCertificado = async () => {
  if (!form.value?.servidores?.trim()) {
    message.warning('Informe o servidor primeiro')
    return
  }
  buscandoCert.value = true
  try {
    certificados.value = await Infra.certificadosAd(form.value.servidores, form.value.porta, form.value.seguranca)
    // a CA é o que se quer guardar; sem CA na cadeia (certificado autoassinado), o próprio certificado do servidor
    const cas = certificados.value.map((c, i) => (c.ca ? i : -1)).filter((i) => i >= 0)
    certEscolhidos.value = cas.length ? cas : [0]
    certAberto.value = true
  } finally {
    buscandoCert.value = false
  }
}

const confiar = () => {
  form.value!.certificadoCa = certEscolhidos.value.map((i) => certificados.value[i].pem).join('')
  certAberto.value = false
  message.success('Certificado colocado na tela. Teste e salve.')
}

const sincronizar = async () => {
  sincronizando.value = true
  try {
    const r = await Infra.sincronizarAd()
    const texto = `${r.lidas} conta(s) lidas, ${r.sincronizadas} atualizadas, ${r.ignoradas} ignoradas, ${r.desativadas} desativadas`
    if (r.desativacaoSuspensa) {
      message.warning(`${texto}. Muitas contas sumiram de uma vez e a desativação foi suspensa: confira o BaseDN e o filtro.`)
    } else {
      message.success(texto)
    }
  } finally {
    sincronizando.value = false
  }
}

onMounted(carregar)
</script>

<style lang="scss" scoped>
.situacao {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
}

.ajuda {
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}

.linha-botoes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  margin-top: 8px;
}

.grupos {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.grupo {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr) auto;
  gap: 8px;
}

.acoes {
  position: sticky;
  bottom: 0;
  z-index: 2;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  /* à direita fica o botão flutuante de voltar ao topo */
  padding: 12px 72px 12px 16px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
}

.resultado {
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.etapa {
  margin: 4px 0;
  font-size: 13px;

  &.ok {
    color: var(--el-color-success);
  }

  &.erro {
    color: var(--el-color-danger);
  }
}

.cert {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.sha {
  font-family: monospace;
  word-break: break-all;
}

code {
  padding: 0 4px;
  background: var(--el-fill-color);
  border-radius: 3px;
}
</style>
