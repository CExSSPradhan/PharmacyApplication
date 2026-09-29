import {readFileSync,writeFileSync,existsSync,mkdirSync,chmodSync} from 'node:fs';
import {randomBytes} from 'node:crypto';
const root=new URL('../',import.meta.url),file=new URL('.env',root);
let text=readFileSync(existsSync(file)?file:new URL('.env.example',root),'utf8');
for(const key of ['DB_PASSWORD','OWNER_PASSWORD','REDIS_PASSWORD','RABBITMQ_PASSWORD','METRICS_TOKEN','GRAFANA_PASSWORD']){
 const pattern=new RegExp(`^${key}=(.*)$`,'m');const match=text.match(pattern);
 if(!match)text+=`\n${key}=${randomBytes(24).toString('hex')}\n`;
 else if(!match[1])text=text.replace(pattern,`${key}=${randomBytes(24).toString('hex')}`);
}
writeFileSync(file,text,{mode:0o600});chmodSync(file,0o600);
mkdirSync(new URL('.runtime/',root),{recursive:true,mode:0o700});
const token=text.match(/^METRICS_TOKEN=(.*)$/m)?.[1];
writeFileSync(new URL('.runtime/metrics-token',root),token+'\n',{mode:0o644});
console.log('Private credentials ready in .env. Existing values were preserved.');
