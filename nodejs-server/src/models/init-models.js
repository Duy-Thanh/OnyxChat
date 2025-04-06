const { DataTypes } = require('sequelize');

module.exports = (sequelize) => {
  // User model
  const User = sequelize.define('User', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    username: {
      type: DataTypes.STRING,
      allowNull: false,
      unique: true
    },
    email: {
      type: DataTypes.STRING,
      allowNull: false,
      unique: true
    },
    passwordHash: {
      type: DataTypes.STRING,
      allowNull: false
    },
    displayName: {
      type: DataTypes.STRING,
      allowNull: false
    },
    isActive: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    lastActiveAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    },
    createdAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    },
    updatedAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    }
  }, {
    tableName: 'users',
    timestamps: true,
    hooks: {
      beforeUpdate: (user) => {
        user.updatedAt = new Date();
      }
    }
  });

  // Add instance methods
  User.prototype.toProfile = function() {
    return {
      id: this.id,
      username: this.username,
      displayName: this.displayName,
      isActive: this.isActive,
      lastActiveAt: this.lastActiveAt
    };
  };

  // Message model
  const Message = sequelize.define('Message', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    senderId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    recipientId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    content: {
      type: DataTypes.TEXT,
      allowNull: false
    },
    contentType: {
      type: DataTypes.STRING,
      defaultValue: 'text'
    },
    encrypted: {
      type: DataTypes.BOOLEAN,
      defaultValue: true
    },
    sent: {
      type: DataTypes.BOOLEAN,
      defaultValue: true
    },
    received: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    read: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    deleted: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    receivedAt: {
      type: DataTypes.DATE,
      allowNull: true
    },
    readAt: {
      type: DataTypes.DATE,
      allowNull: true
    },
    deletedAt: {
      type: DataTypes.DATE,
      allowNull: true
    },
    createdAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    },
    updatedAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    }
  }, {
    tableName: 'messages',
    timestamps: true
  });

  // RefreshToken model
  const RefreshToken = sequelize.define('RefreshToken', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    token: {
      type: DataTypes.TEXT,
      allowNull: false,
      unique: true
    },
    userId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    expiresAt: {
      type: DataTypes.DATE,
      allowNull: false
    },
    revoked: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    revokedAt: {
      type: DataTypes.DATE,
      allowNull: true
    },
    createdAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    },
    updatedAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    }
  }, {
    tableName: 'refresh_tokens',
    timestamps: true
  });

  // Define associations
  User.hasMany(Message, { foreignKey: 'senderId', as: 'sentMessages' });
  User.hasMany(Message, { foreignKey: 'recipientId', as: 'receivedMessages' });
  Message.belongsTo(User, { foreignKey: 'senderId', as: 'sender' });
  Message.belongsTo(User, { foreignKey: 'recipientId', as: 'recipient' });
  User.hasMany(RefreshToken, { foreignKey: 'userId' });
  RefreshToken.belongsTo(User, { foreignKey: 'userId' });

  // Contact model
  const Contact = sequelize.define('Contact', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    userId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    contactId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    nickname: {
      type: DataTypes.STRING,
      allowNull: true
    },
    blocked: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    createdAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    },
    updatedAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    }
  }, {
    tableName: 'contacts',
    timestamps: true
  });

  // Add Contact associations
  User.hasMany(Contact, { foreignKey: 'userId' });
  Contact.belongsTo(User, { foreignKey: 'userId' });
  Contact.belongsTo(User, { foreignKey: 'contactId', as: 'contactUser' });

  // FriendRequest model
  const FriendRequest = sequelize.define('FriendRequest', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    senderId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    receiverId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    status: {
      type: DataTypes.ENUM('pending', 'accepted', 'rejected'),
      defaultValue: 'pending'
    },
    message: {
      type: DataTypes.STRING,
      allowNull: true
    },
    createdAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    },
    updatedAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    }
  }, {
    tableName: 'friend_requests',
    timestamps: true,
    indexes: [
      {
        unique: true,
        fields: ['senderId', 'receiverId'],
        where: {
          status: 'pending'
        }
      }
    ]
  });

  // Add FriendRequest associations
  User.hasMany(FriendRequest, { foreignKey: 'senderId', as: 'sentFriendRequests' });
  User.hasMany(FriendRequest, { foreignKey: 'receiverId', as: 'receivedFriendRequests' });
  FriendRequest.belongsTo(User, { foreignKey: 'senderId', as: 'sender' });
  FriendRequest.belongsTo(User, { foreignKey: 'receiverId', as: 'receiver' });

  // UserKey model
  const UserKey = sequelize.define('UserKey', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    userId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    publicKey: {
      type: DataTypes.TEXT,
      allowNull: false
    },
    privateKey: {
      type: DataTypes.TEXT,
      allowNull: true
    },
    active: {
      type: DataTypes.BOOLEAN,
      defaultValue: true
    },
    createdAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    },
    updatedAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    }
  }, {
    tableName: 'user_keys',
    timestamps: true
  });

  // OneTimePreKey model
  const OneTimePreKey = sequelize.define('OneTimePreKey', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    userId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    keyId: {
      type: DataTypes.INTEGER,
      allowNull: false
    },
    key: {
      type: DataTypes.TEXT,
      allowNull: false
    },
    used: {
      type: DataTypes.BOOLEAN,
      defaultValue: false
    },
    createdAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    },
    updatedAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    }
  }, {
    tableName: 'one_time_prekeys',
    timestamps: true,
    indexes: [
      {
        unique: true,
        fields: ['userId', 'keyId']
      }
    ]
  });

  // Session model
  const Session = sequelize.define('Session', {
    id: {
      type: DataTypes.UUID,
      defaultValue: DataTypes.UUIDV4,
      primaryKey: true
    },
    userId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    otherUserId: {
      type: DataTypes.UUID,
      allowNull: false,
      references: {
        model: 'users',
        key: 'id'
      }
    },
    sessionData: {
      type: DataTypes.TEXT,
      allowNull: false
    },
    active: {
      type: DataTypes.BOOLEAN,
      defaultValue: true
    },
    createdAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    },
    updatedAt: {
      type: DataTypes.DATE,
      defaultValue: DataTypes.NOW
    }
  }, {
    tableName: 'sessions',
    timestamps: true,
    indexes: [
      {
        unique: true,
        fields: ['userId', 'otherUserId']
      }
    ]
  });

  // Add associations for new models
  User.hasMany(UserKey, { foreignKey: 'userId' });
  UserKey.belongsTo(User, { foreignKey: 'userId' });
  
  User.hasMany(OneTimePreKey, { foreignKey: 'userId' });
  OneTimePreKey.belongsTo(User, { foreignKey: 'userId' });
  
  User.hasMany(Session, { foreignKey: 'userId' });
  Session.belongsTo(User, { foreignKey: 'userId' });
  Session.belongsTo(User, { foreignKey: 'otherUserId', as: 'otherUser' });

  return {
    User,
    Message,
    RefreshToken,
    Contact,
    UserKey,
    OneTimePreKey,
    Session,
    FriendRequest
  };
}; 