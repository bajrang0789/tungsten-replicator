module ConfigureMessages
  attr_reader :errors
  
  def initialize
    reset_errors()
  end
  
  def reset_errors
    @errors = []
  end
  
  def is_valid?()
    (@errors == nil || @errors.length() == 0)
  end
  
  def output(message, level = Logger::INFO)
    Configurator.instance.write(message, level, get_message_hostname(), true)
  end
  
  def info(message)
    Configurator.instance.info(message, get_message_hostname())
  end
  
  def warning(message)
    Configurator.instance.warning(message, get_message_hostname())
  end
  
  def error(message, e = nil)
    Configurator.instance.error(message, get_message_hostname())
    
    unless @errors
      @errors = []
    end
    
    store_error_object(message, e)
  end

  def store_error_object(message, e = nil)
    if e == nil
      e = build_error_object(message)
    end
    
    @errors.push(e)
  end
  
  def build_error_object(message)
    get_error_object_class().new(message, get_message_hostname())
  end
  
  def get_error_object_class
    RemoteError
  end
  
  def debug(message)
    Configurator.instance.debug(message, get_message_hostname())
  end
  
  def get_message_hostname
    nil
  end
  
  def output_errors
    host_errors = Hash.new()
    generic_errors = []
    
    @errors.each{
      |error|
      
      if (error.is_a?(ValidationError) || error.is_a?(RemoteError)) && error.host.to_s() != ""
        unless host_errors.has_key?(error.host)
          host_errors[error.host] = []
        end
        host_errors[error.host] << error
      else  
        generic_errors << error
      end
    }
    
    host_errors.each_key{
      |host|
      
      $i = 0

      Configurator.instance.write_header("Errors for #{host}", Logger::ERROR)
      until $i >= host_errors[host].length() do
        $host_error = host_errors[host][$i]
        $i+=1
        $next_host_error = host_errors[host][$i]
        
        Configurator.instance.error($host_error.message, host)
        
        if $next_host_error == nil || $host_error.check != $next_host_error.check
          if $host_error.is_a?(ValidationError)
            help = $host_error.get_help()
            unless help == nil || help.empty?()
              puts help.join("\n")
            end
        
            # Disable this section for now
            if $host_error.check.support_remote_fix && Configurator.instance.is_interactive?() && false
              execute_fix = input_value("Do you want the script to automatically fix this?", "false")
            end
        
            unless help == nil || help.empty?()
              Configurator.instance.write_divider(Logger::ERROR)
            end
          end
        end
      end
    }
    
    unless generic_errors.empty?()
      previous_help = nil
      
      Configurator.instance.write_header('Errors for the cluster', Logger::ERROR)
      generic_errors.each{
        |generic_error|
        Configurator.instance.error(generic_error.message)
      }
    end
  end
end

class RemoteError < StandardError
  attr_reader :message, :host

  def initialize(message, host = nil)
    if host == nil
      host = `hostname`.chomp()
    end
    
    @message=message
    @host=host
  end
end

class RemoteResult
  attr_accessor :messages, :errors
  
  def initialize
    @messages = []
    @errors = []
  end
  
  def output
    @messages.each{
      |message|
      puts message
    }
  end
end